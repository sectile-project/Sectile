package dev.sectile.network.internal.auth

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Base64

/**
 * The server's game session lasts one hour: it must be detected before use.
 */
class JwtClaimsTest {

    private fun token(payload: String): String {
        val header = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("""{"alg":"EdDSA"}""".toByteArray())
        val body = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.toByteArray())
        return "$header.$body.c2ln"
    }

    @Test
    fun `reads the expiration of a token`() {
        val expiry = System.currentTimeMillis() / 1000 + 3600
        assertEquals(expiry, JwtClaims.expiresAt(token("""{"exp":$expiry}""")))
    }

    @Test
    fun `returns zero when the token can not be read`() {
        assertEquals(0L, JwtClaims.expiresAt("not-a-jwt"))
        assertEquals(0L, JwtClaims.expiresAt("a.b.c"))
    }

    @Test
    fun `credentials are invalid when the session token has expired`() {
        val expired = token("""{"exp":${System.currentTimeMillis() / 1000 - 10}}""")
        val credentials = ServerCredentials(
            sessionToken = expired,
            identityToken = "id",
            refreshToken = null,
            profileUuid = "uuid",
            profileName = "name",
            expiresAt = null,
        )
        assertFalse(credentials.valid)
    }

    @Test
    fun `credentials are valid while the session token is alive`() {
        val alive = token("""{"exp":${System.currentTimeMillis() / 1000 + 3600}}""")
        val credentials = ServerCredentials(
            sessionToken = alive,
            identityToken = "id",
            refreshToken = null,
            profileUuid = "uuid",
            profileName = "name",
            expiresAt = null,
        )
        assertTrue(credentials.valid)
    }
}
