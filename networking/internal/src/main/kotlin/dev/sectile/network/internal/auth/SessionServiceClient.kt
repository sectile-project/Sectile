package dev.sectile.network.internal.auth

import dev.sectile.kernel.config.ConfigSection
import dev.sectile.kernel.config.ConfigService
import dev.sectile.kernel.config.ConfigServiceRegistry
import dev.sectile.kernel.config.section.MapConfigSection
import dev.sectile.kernel.logger.Logger
import dev.sectile.network.internal.auth.jwt.JwkSet
import java.net.URI
import java.net.http.HttpRequest
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.UUID

/**
 * Server game session (the one used as Bearer against the Session Service).
 *
 * @param sessionToken server session token.
 * @param identityToken identity token sent to the client in `AuthGrant`.
 * @param expiresAt expiration date (ISO-8601), if reported by the service.
 */
data class GameSession(
    val sessionToken: String,
    val identityToken: String,
    val expiresAt: String?,
)

/**
 * Game profile of an account.
 */
data class GameProfile(
    val uuid: String,
    val username: String,
)

/**
 * HTTP client for the Session Service.
 *
 * Endpoints used by login:
 *  - `POST /server-join/auth-grant` -> `{ "authorizationGrant": "..." }`
 *  - `POST /server-join/auth-token` -> `{ "accessToken": "..." }`
 *  - `GET  /.well-known/jwks.json`  -> `{ "keys": [ ... ] }`
 *
 * The calls are blocking on purpose: the login handler runs them outside the
 * Netty event loop. All instances share [AuthHttp.client].
 */
class SessionServiceClient(
    baseUrl: String,
    private val timeout: Duration = Duration.ofSeconds(30),
    private val userAgent: String = "Sectile",
    private val logger: Logger,
) {

    private val baseUrl: String = baseUrl.removeSuffix("/")

    private val authGrantUri = URI.create("${this.baseUrl}/server-join/auth-grant")
    private val authTokenUri = URI.create("${this.baseUrl}/server-join/auth-token")
    private val jwksUri = URI.create("${this.baseUrl}/.well-known/jwks.json")
    private val newSessionUri = URI.create("${this.baseUrl}/game-session/new")
    private val refreshSessionUri = URI.create("${this.baseUrl}/game-session/refresh")

    private val json: ConfigService get() = ConfigServiceRegistry["json"]

    /**
     * Requests an authorization grant for the player who is connecting.
     *
     * @param identityToken player's identity token.
     * @param audience server audience.
     * @param bearerToken server session token.
     * @return the grant, or `null` if the call fails.
     */
    fun requestAuthorizationGrant(
        identityToken: String,
        audience: String,
        bearerToken: String,
    ): String? {
        val body = jsonBody("identityToken" to identityToken, "aud" to audience)
        val response = call("auth-grant", post(authGrantUri, body, bearerToken)) ?: return null
        return json.load(response).getString("authorizationGrant")
    }

    /**
     * Exchanges a grant for an access token bound to the server's certificate.
     *
     * @param authorizationGrant grant sent by the client.
     * @param x509Fingerprint SHA-256 fingerprint in base64url of the server's certificate.
     * @param bearerToken server session token.
     */
    fun exchangeAuthGrantForToken(
        authorizationGrant: String,
        x509Fingerprint: String,
        bearerToken: String,
    ): String? {
        val body = jsonBody("authorizationGrant" to authorizationGrant, "x509Fingerprint" to x509Fingerprint)
        val response = call("auth-token", post(authTokenUri, body, bearerToken)) ?: return null
        return json.load(response).getString("accessToken")
    }

    /** Downloads the JWKS from the Session Service. */
    fun fetchJwks(): JwkSet? {
        val request = base(jwksUri).GET().build()
        val response = call("jwks", request, quiet = true) ?: return null
        return try {
            JwkSet.parse(json.load(response))
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Creates a game session for a profile.
     *
     * The session is what authenticates the server against the Session Service: it returns the
     * session token (Bearer) and the identity token that is sent to the client in `AuthGrant`.
     *
     * @param oauthAccessToken OAuth token of the account owner.
     * @param profileUuid UUID of the game profile.
     */
    fun createGameSession(oauthAccessToken: String, profileUuid: UUID): GameSession? {
        val request = post(newSessionUri, jsonBody("uuid" to profileUuid.toString()), oauthAccessToken)
        val response = call("game-session/new", request, accepted = 201) ?: return null
        return parseSession(response)
    }

    /**
     * Refreshes the server's game session.
     *
     * @param sessionToken current session token.
     * @return the new session, or `null` if the service rejects it.
     */
    fun refreshGameSession(sessionToken: String): GameSession? {
        val request = base(refreshSessionUri)
            .header("Authorization", "Bearer $sessionToken")
            .POST(HttpRequest.BodyPublishers.noBody())
            .build()
        val response = call("game-session/refresh", request) ?: return null
        return parseSession(response)
    }

    /**
     * Lists the game profiles of an account.
     *
     * @param oauthAccessToken account's OAuth token.
     * @param accountDataUrl base URL of the account data service.
     */
    fun getGameProfiles(oauthAccessToken: String, accountDataUrl: String): List<GameProfile> {
        val request = base(URI.create("${accountDataUrl.removeSuffix("/")}/my-account/get-profiles"))
            .header("Authorization", "Bearer $oauthAccessToken")
            .GET()
            .build()
        val response = call("get-profiles", request) ?: return emptyList()
        return try {
            val profiles = json.load(response)["profiles"] as? Collection<*> ?: return emptyList()
            profiles.mapNotNull { element ->
                val section = element as? ConfigSection ?: return@mapNotNull null
                val uuid = section.getString("uuid") ?: return@mapNotNull null
                GameProfile(uuid, section.getString("username", ""))
            }
        } catch (e: Exception) {
            logger.error("Error on get game profiles", e)
            emptyList()
        }
    }

    private fun parseSession(body: ByteArray): GameSession? = try {
        val section = json.load(body)
        val sessionToken = section.getString("sessionToken")
        val identityToken = section.getString("identityToken")
        if (sessionToken.isNullOrEmpty() || identityToken.isNullOrEmpty()) {
            null
        } else {
            GameSession(sessionToken, identityToken, section.getString("expiresAt"))
        }
    } catch (e: Exception) {
        logger.warn("Session Service returned an unreadable session: ${e.javaClass.simpleName}")
        null
    }

    private fun jsonBody(vararg entries: Pair<String, String>): String = json.saveIntoString(MapConfigSection(linkedMapOf(*entries)))

    private fun base(uri: URI): HttpRequest.Builder = HttpRequest.newBuilder(uri)
        .header("Accept", "application/json")
        .header("User-Agent", userAgent)
        .timeout(timeout)

    private fun post(uri: URI, body: String, bearerToken: String): HttpRequest = base(uri)
        .header("Content-Type", "application/json")
        .header("Authorization", "Bearer $bearerToken")
        .POST(HttpRequest.BodyPublishers.ofString(body))
        .build()

    /**
     * Executes the request with the shared client.
     *
     * @return the body if the status is 200 (or [accepted]), `null` in any other case.
     */
    private fun call(name: String, request: HttpRequest, accepted: Int = 200, quiet: Boolean = false): ByteArray? = try {
        val response = AuthHttp.send(request)
        if (response.status == 200 || response.status == accepted) {
            response.body
        } else {
            if (!quiet) {
                // The service's error body does not contain secrets; it is truncated just in case.
                val preview = String(response.body, 0, minOf(response.body.size, 512), StandardCharsets.UTF_8)
                logger.warn("Session Service $name failed: HTTP ${response.status} - $preview")
            }
            null
        }
    } catch (_: InterruptedException) {
        Thread.currentThread().interrupt()
        null
    } catch (e: Exception) {
        if (!quiet) logger.warn("Session Service $name failed: ${e.javaClass.simpleName}: ${e.message}")
        null
    }
}
