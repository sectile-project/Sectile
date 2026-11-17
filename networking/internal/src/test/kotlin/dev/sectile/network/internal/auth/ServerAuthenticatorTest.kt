package dev.sectile.network.internal.auth

import dev.sectile.kernel.config.ConfigSection
import dev.sectile.kernel.config.ConfigServiceRegistry
import dev.sectile.sdk.bundled.config.json.JsonConfigService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

/**
 * Tests for server authentication: credential persistence and format of the
 * responses from Hytale services.
 */
class ServerAuthenticatorTest {

    companion object {
        @JvmStatic
        @BeforeAll
        fun registerConfigService() {
            ConfigServiceRegistry.register(JsonConfigService)
        }
    }

    private fun parseJson(text: String): ConfigSection = ConfigServiceRegistry["json"].load(text.toByteArray(Charsets.UTF_8))

    @Test
    fun `credentials round trip through disk`() {
        val file = Path.of(ServerAuthenticator.CREDENTIALS_FILE)
        val existed = Files.exists(file)
        val previous = if (existed) Files.readString(file) else null

        try {
            val credentials = ServerCredentials(
                sessionToken = "session-token",
                identityToken = "identity-token",
                refreshToken = "refresh-token",
                profileUuid = "11111111-2222-3333-4444-555555555555",
                profileName = "ichocomilk",
                expiresAt = "2030-01-01T00:00:00Z",
            )
            ServerAuthenticator.save(credentials)

            val loaded = ServerAuthenticator.load()
            assertNotNull(loaded)
            assertEquals("session-token", loaded!!.sessionToken)
            assertEquals("identity-token", loaded.identityToken)
            assertEquals("refresh-token", loaded.refreshToken)
            assertEquals("ichocomilk", loaded.profileName)
            assertEquals("2030-01-01T00:00:00Z", loaded.expiresAt)

            // Updating only the tokens preserves the rest.
            ServerAuthenticator.updateTokens("new-session", "new-identity", "2031-01-01T00:00:00Z")
            val updated = ServerAuthenticator.load()!!
            assertEquals("new-session", updated.sessionToken)
            assertEquals("new-identity", updated.identityToken)
            assertEquals("ichocomilk", updated.profileName)
        } finally {
            if (existed) {
                Files.writeString(file, previous!!)
            } else {
                Files.deleteIfExists(file)
            }
        }
    }

    @Test
    fun `parses the oauth token response`() {
        val json = parseJson(
            """{"access_token":"at","refresh_token":"rt","id_token":"it","expires_in":3600}""",
        )
        assertEquals("at", json.getString("access_token"))
        assertEquals("rt", json.getString("refresh_token"))
        assertEquals("it", json.getString("id_token"))
        assertEquals(3600, json.getInt("expires_in"))
    }

    @Test
    fun `parses the device authorization response`() {
        val json = parseJson(
            """
            {"device_code":"dc","user_code":"ABCD-1234","verification_uri":"https://accounts.hytale.com/device",
             "verification_uri_complete":"https://accounts.hytale.com/device?user_code=ABCD-1234",
             "expires_in":900,"interval":5}
            """.trimIndent(),
        )
        assertEquals("dc", json.getString("device_code"))
        assertEquals("ABCD-1234", json.getString("user_code"))
        assertEquals(900, json.getInt("expires_in"))
        assertEquals(5, json.getInt("interval"))
    }

    @Test
    fun `parses the game session and profile responses`() {
        val session = parseJson("""{"sessionToken":"st","identityToken":"it","expiresAt":"2030-01-01T00:00:00Z"}""")
        assertEquals("st", session.getString("sessionToken"))
        assertEquals("it", session.getString("identityToken"))

        val profiles = parseJson(
            """{"owner":"11111111-2222-3333-4444-555555555555","profiles":[{"uuid":"11111111-2222-3333-4444-555555555555","username":"ichocomilk"}]}""",
        )
        val list = profiles["profiles"] as List<*>
        assertEquals(1, list.size)
        assertEquals("ichocomilk", (list[0] as ConfigSection).getString("username"))
    }

    @Test
    fun `the oauth endpoints match the official ones`() {
        assertEquals("https://oauth.accounts.hytale.com/oauth2/auth", OAuthClient.OAUTH_AUTH_URL)
        assertEquals("https://oauth.accounts.hytale.com/oauth2/token", OAuthClient.OAUTH_TOKEN_URL)
        assertEquals("https://oauth.accounts.hytale.com/oauth2/device/auth", OAuthClient.DEVICE_AUTH_URL)
        assertEquals("https://sessions.hytale.com", OAuthClient.SESSION_SERVICE_URL)
        assertEquals("hytale-server", OAuthClient.CLIENT_ID)
        assertTrue(OAuthClient.SCOPES.contains("auth:server"))
    }
}
