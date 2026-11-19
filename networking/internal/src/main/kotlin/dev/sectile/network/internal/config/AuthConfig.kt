package dev.sectile.network.internal.config

import java.time.Duration

/**
 * Server authentication mode.
 *
 *  - [INSECURE]: the player's identity is not validated (development). The server requests the
 *    player's options with `RequestInsecurePlayerOptions`.
 *  - [AUTHENTICATED]: the client must send a valid identity token and mutual
 *    authentication is completed (AuthGrant / AuthToken / ServerAuthToken).
 */
enum class AuthMode {
    INSECURE,
    AUTHENTICATED,
}

/**
 * Authentication configuration.
 *
 * @param mode authentication mode.
 * @param sessionServiceUrl base URL of the Session Service (it is also the expected `iss`).
 * @param audience expected audience in the tokens (`aud`).
 * @param serverSessionToken server session token (Bearer for the Session Service).
 * @param serverIdentityToken server identity token (sent in `AuthGrant`).
 * @param password server password; empty = no password is requested.
 * @param httpTimeout maximum time for HTTP calls to the Session Service. It must be
 *        shorter than the connection read timeout, so that a failure is reported before
 *        the client runs out of time.
 */
class AuthConfig(
    val mode: AuthMode,
    val sessionServiceUrl: String?,
    val audience: String?,
    val serverSessionToken: String?,
    val serverIdentityToken: String?,
    val password: String?,
    val httpTimeout: Duration = Duration.ofSeconds(15),
) {

    val authenticated: Boolean get() = mode == AuthMode.AUTHENTICATED
    val passwordRequired: Boolean get() = !password.isNullOrEmpty()

    fun validate(): String? = when (mode) {
        AuthMode.INSECURE -> null

        AuthMode.AUTHENTICATED -> when {
            sessionServiceUrl.isNullOrEmpty() -> "auth.session-service-url is required"
            audience.isNullOrEmpty() -> "auth.audience is required"
            else -> null
        }
    }

    companion object {
        const val SCOPE_CLIENT: String = "hytale:client"
        const val SCOPE_EDITOR: String = "hytale:editor"
    }
}
