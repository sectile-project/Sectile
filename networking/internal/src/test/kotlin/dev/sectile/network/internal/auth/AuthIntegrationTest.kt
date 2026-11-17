package dev.sectile.network.internal.auth

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import dev.sectile.kernel.config.ConfigSection
import dev.sectile.kernel.config.ConfigServiceRegistry
import dev.sectile.network.internal.auth.jwt.JWTValidator
import dev.sectile.network.internal.auth.jwt.JwksCache
import dev.sectile.network.internal.config.AuthConfig
import dev.sectile.sdk.bundled.config.json.JsonConfigService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import java.util.UUID

/**
 * Integration test of the authentication path: JWKS over HTTP, JWT validation, and
 * calls to the Session Service, against a locally simulated service.
 */
class AuthIntegrationTest {

    companion object {
        @JvmStatic
        @BeforeAll
        fun registerConfigService() {
            ConfigServiceRegistry.register(JsonConfigService)
        }
    }

    private fun parseJson(text: String): ConfigSection = ConfigServiceRegistry["json"].load(text.toByteArray(Charsets.UTF_8))

    private lateinit var server: HttpServer
    private lateinit var baseUrl: String
    private lateinit var keyPair: KeyPair

    @BeforeEach
    fun setUp() {
        keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()

        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/.well-known/jwks.json") { exchange ->
            val x = Base64.getUrlEncoder().withoutPadding().encodeToString(rawPublicKey())
            respond(exchange, """{"keys":[{"alg":"EdDSA","crv":"Ed25519","kid":"test-1","kty":"OKP","use":"sig","x":"$x"}]}""")
        }
        server.createContext("/server-join/auth-grant") { exchange ->
            respond(exchange, """{"authorizationGrant":"grant-123"}""")
        }
        server.createContext("/server-join/auth-token") { exchange ->
            respond(exchange, """{"accessToken":"server-access-token"}""")
        }
        server.createContext("/game-session/new") { exchange ->
            respond(
                exchange,
                """{"sessionToken":"game-session-token","identityToken":"game-identity-token",""" +
                    """"expiresAt":"2030-01-01T00:00:00Z"}""",
            )
        }
        server.createContext("/my-account/get-profiles") { exchange ->
            respond(
                exchange,
                """{"owner":"11111111-2222-3333-4444-555555555555",""" +
                    """"profiles":[{"uuid":"11111111-2222-3333-4444-555555555555","username":"ichocomilk"}]}""",
            )
        }
        server.start()
        baseUrl = "http://127.0.0.1:${server.address.port}"
    }

    @AfterEach
    fun tearDown() {
        server.stop(0)
    }

    private fun rawPublicKey(): ByteArray {
        val encoded = keyPair.public.encoded
        return encoded.copyOfRange(encoded.size - 32, encoded.size)
    }

    private fun respond(exchange: HttpExchange, body: String) {
        val bytes = body.toByteArray()
        exchange.responseHeaders.add("Content-Type", "application/json")
        exchange.sendResponseHeaders(200, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    /** Builds a JWT signed with the test key. */
    private fun buildJwt(claims: Map<String, Any?>): String {
        val header = encodeSegment("""{"alg":"EdDSA","kid":"test-1","typ":"JWT"}""")
        val payload = encodeSegment(buildJson(claims))
        val signingInput = "$header.$payload"
        val signer = Signature.getInstance("Ed25519")
        signer.initSign(keyPair.private)
        signer.update(signingInput.toByteArray(Charsets.US_ASCII))
        val signature = Base64.getUrlEncoder().withoutPadding().encodeToString(signer.sign())
        return "$signingInput.$signature"
    }

    private fun encodeSegment(value: String): String = Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(Charsets.UTF_8))

    private fun buildJson(values: Map<String, Any?>): String {
        val parts = values.entries.joinToString(",") { (key, value) ->
            val encoded = when (value) {
                is String -> "\"${value}\""
                is Number -> value.toString()
                is Map<*, *> -> buildJson(value.entries.associate { it.key.toString() to it.value })
                is List<*> -> value.joinToString(",") { buildJson(mapOf("v" to it)).removePrefix("{\"v\":").removeSuffix("}") }
                else -> "null"
            }
            "\"$key\":$encoded"
        }
        return "{$parts}"
    }

    private fun validClaims(): Map<String, Any?> = mapOf(
        "iss" to baseUrl,
        "sub" to UUID.randomUUID().toString(),
        "exp" to (System.currentTimeMillis() / 1000 + 3600),
        "iat" to (System.currentTimeMillis() / 1000),
        "scope" to "hytale:client",
        "profile" to mapOf("username" to "ichocomilk"),
    )

    @Test
    fun `validates an identity token against a live jwks`() {
        val logger = TestLogger()
        val validator = JWTValidator(JwksCache(SessionServiceClient(baseUrl, logger = logger)), baseUrl, "aud", logger)
        val token = buildJwt(validClaims())

        val claims = validator.validateIdentityToken(token)
        assertNotNull(claims, logger.dump())
        assertEquals("ichocomilk", claims!!.username)
        assertTrue(claims.hasScope(AuthConfig.SCOPE_CLIENT))
        assertNotNull(claims.subject)
    }

    @Test
    fun `rejects a token signed with another key`() {
        val logger = TestLogger()
        val validator = JWTValidator(JwksCache(SessionServiceClient(baseUrl, logger = logger)), baseUrl, "aud", logger)

        val other = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        val signer = Signature.getInstance("Ed25519")
        signer.initSign(other.private)
        val header = """{"alg":"EdDSA","kid":"test-1","typ":"JWT"}"""
        val payload = buildJson(validClaims())
        val signingInput = "$header.$payload"
        signer.update(signingInput.toByteArray(Charsets.US_ASCII))
        val signature = Base64.getUrlEncoder().withoutPadding().encodeToString(signer.sign())

        assertNull(validator.validateIdentityToken("$signingInput.$signature"))
    }

    @Test
    fun `rejects a token with the wrong issuer`() {
        val logger = TestLogger()
        val validator = JWTValidator(JwksCache(SessionServiceClient(baseUrl, logger = logger)), baseUrl, "aud", logger)
        val claims = validClaims().toMutableMap().apply { this["iss"] = "https://otro.example" }
        assertNull(validator.validateIdentityToken(buildJwt(claims)))
    }

    @Test
    fun `reads the audience as a string or as a list`() {
        assertEquals("abc", JWTValidator.audienceOf(parseJson("{\"aud\":\"abc\"}")["aud"]))
        assertEquals("abc", JWTValidator.audienceOf(parseJson("{\"aud\":[\"abc\",\"def\"]}")["aud"]))
        assertNull(JWTValidator.audienceOf(parseJson("{\"other\":1}")["other"]))
        assertNull(JWTValidator.audienceOf(null))
    }

    @Test
    fun `requests an authorization grant and exchanges it for a token`() {
        val client = SessionServiceClient(baseUrl, logger = TestLogger())

        val grant = client.requestAuthorizationGrant("identity-token", "audience", "bearer")
        assertEquals("grant-123", grant)

        val accessToken = client.exchangeAuthGrantForToken("grant-123", "fingerprint", "bearer")
        assertEquals("server-access-token", accessToken)
    }

    @Test
    fun `creates a game session and lists the profiles`() {
        val client = SessionServiceClient(baseUrl, logger = TestLogger())

        val session = client.createGameSession("oauth-token", UUID.randomUUID())
        assertNotNull(session)
        assertEquals("game-session-token", session!!.sessionToken)
        assertEquals("game-identity-token", session.identityToken)

        val profiles = client.getGameProfiles("oauth-token", baseUrl)
        assertEquals(1, profiles.size)
        assertEquals("ichocomilk", profiles[0].username)
    }
}
