package dev.sectile.network.internal.auth

import dev.sectile.kernel.config.ConfigServiceRegistry
import dev.sectile.kernel.config.section.MapConfigSection
import dev.sectile.kernel.logger.Logger
import dev.sectile.kernel.logger.logger
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64
import java.util.UUID
import kotlin.io.path.writeText

/**
 * Server credentials for the Session Service.
 *
 * @param sessionToken server session (Bearer) token.
 * @param identityToken identity token sent to the client in `AuthGrant`.
 * @param refreshToken OAuth refresh token, if obtained.
 * @param profileUuid UUID of the game profile that has been signed in with.
 * @param profileName name of the game profile.
 * @param expiresAt expiration of the game session (ISO-8601), if reported by the service.
 */
data class ServerCredentials(
    val sessionToken: String,
    val identityToken: String,
    val refreshToken: String?,
    val profileUuid: String,
    val profileName: String,
    val expiresAt: String?,
) {
    val expiresAtEpoch: Long
        get() = JwtClaims.expiresAt(sessionToken)

    val valid: Boolean
        get() {
            val expiry = expiresAtEpoch
            return expiry > 0L && expiry - ServerAuthenticator.SECONDS_UNTIL_TOKEN_EXPIRE > System.currentTimeMillis() / 1000
        }
}

/**
 * Minimal reading of a JWT's claims (without verifying the signature).
 */
object JwtClaims {
    /** Returns the `exp` claim (epoch seconds), or 0 if it cannot be read. */
    fun expiresAt(token: String): Long {
        // No `split`: the two dots are located and only the payload is decoded.
        val first = token.indexOf('.')
        if (first <= 0) return 0L
        val second = token.indexOf('.', first + 1)
        if (second < 0 || token.indexOf('.', second + 1) >= 0) return 0L
        return try {
            val payload = Base64.getUrlDecoder().decode(token.substring(first + 1, second))
            (ConfigServiceRegistry["json"].load(payload)["exp"] as? Number)?.toLong() ?: 0L
        } catch (_: Exception) {
            0L
        }
    }
}

/**
 * Server authentication against Hytale services.
 *
 * The official client only accepts authenticated servers (the "insecure" mode is reserved for
 * development and the client rejects it when entering the world). Authenticating the server means:
 *
 *  1. signing in to the owner's account with the device authorization flow,
 *  2. selecting the game profile,
 *  3. creating the game session (this yields the session token and the identity token).
 *
 * The credentials are saved in `auth.json` so that the process does not have to be repeated on every
 * startup.
 */
object ServerAuthenticator {

    const val SECONDS_UNTIL_TOKEN_EXPIRE: Long = 300
    const val CREDENTIALS_FILE: String = "auth.json"

    private const val DEVICE_POLL_INTERVAL_SECONDS = 5
    private const val MAX_DEVICE_WAIT_SECONDS = 900

    /** A single (stateless) OAuth client for login and all refreshes. */
    private val oauth: OAuthClient by lazy { OAuthClient() }

    /** In-memory credentials: avoids re-reading and parsing `auth.json` on every refresh. */
    @Volatile
    private var cached: ServerCredentials? = null

    fun authenticate(logger: Logger): ServerCredentials? {
        val oauth = this.oauth

        logger.info("Starting device authorization...")
        val device = oauth.requestDeviceAuthorization()
        if (device == null) {
            logger.error("Could not start the device authorization flow")
            return null
        }

        logger.info(
            """
                DEVICE AUTHORIZATION

                  > Visit: ${device.verificationUri}
                  > Enter code: ${device.userCode}
                  > ${device.verificationUriComplete?.let { "Or visit: $it" }}

                Waiting for authorization (expires in ${device.expiresIn} seconds)...
            """,
        )

        val tokens = pollDeviceToken(oauth, device, logger) ?: run {
            logger.error("Device authorization failed")
            return null
        }

        val accessToken = tokens.accessToken
        if (accessToken.isNullOrEmpty()) {
            logger.error("Device authorization did not return an access token")
            return null
        }

        val sessionService = SessionServiceClient(OAuthClient.SESSION_SERVICE_URL, logger = logger)
        val profiles = sessionService.getGameProfiles(accessToken, OAuthClient.ACCOUNT_DATA_URL)
        if (profiles.isEmpty()) {
            logger.error("The account has no game profiles")
            return null
        }

        val profile = profiles.first()
        if (profiles.size > 1) {
            logger.info("The account has ${profiles.size} profiles, using '${profile.username}'")
        }

        val profileUuid = try {
            UUID.fromString(profile.uuid)
        } catch (_: IllegalArgumentException) {
            logger.error("The account returned an invalid profile uuid: ${profile.uuid}")
            return null
        }

        val session = sessionService.createGameSession(accessToken, profileUuid)
        if (session == null) {
            logger.error("Could not create the game session")
            return null
        }

        val credentials = ServerCredentials(
            sessionToken = session.sessionToken,
            identityToken = session.identityToken,
            refreshToken = tokens.refreshToken,
            profileUuid = profile.uuid,
            profileName = profile.username,
            expiresAt = session.expiresAt,
        )

        save(credentials)
        logger.info("Server authenticated as '${profile.username}' (${profile.uuid})")
        return credentials
    }

    private fun pollDeviceToken(
        oauth: OAuthClient,
        device: DeviceAuthorization,
        logger: Logger,
    ): OAuthTokens? {
        var intervalInSeconds = maxOf(device.interval, DEVICE_POLL_INTERVAL_SECONDS)
        val deadline = System.currentTimeMillis() + minOf(device.expiresIn, MAX_DEVICE_WAIT_SECONDS) * 1000L

        while (System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(intervalInSeconds * 1000L)
            } catch (_: InterruptedException) {
                return null
            }

            val tokens = oauth.pollDeviceToken(device.deviceCode) ?: continue
            when (tokens.error) {
                null -> return tokens

                "authorization_pending" -> continue

                "slow_down" -> {
                    intervalInSeconds += 5
                    continue
                }

                else -> {
                    logger.error("Device authorization failed: ${tokens.error}")
                    return null
                }
            }
        }
        logger.error("Device authorization expired")
        return null
    }

    fun load(): ServerCredentials? {
        cached?.let { return it }

        val file = Path.of(CREDENTIALS_FILE)
        if (!Files.exists(file)) {
            return null
        }

        return try {
            val credentials = ConfigServiceRegistry["json"].load(file)

            val sessionToken = credentials.getString("sessionToken") ?: return null
            val identityToken = credentials.getString("identityToken") ?: return null

            ServerCredentials(
                sessionToken = sessionToken,
                identityToken = identityToken,
                refreshToken = credentials.getString("refreshToken"),
                profileUuid = credentials.getString("profileUuid") ?: "",
                profileName = credentials.getString("profileName") ?: "",
                expiresAt = credentials.getString("expiresAt"),
            ).also { cached = it }
        } catch (e: Exception) {
            logger().error("Failed to load server credentials", e)
            null
        }
    }

    fun save(credentials: ServerCredentials) {
        cached = credentials
        val section = MapConfigSection(
            linkedMapOf(
                "sessionToken" to credentials.sessionToken,
                "identityToken" to credentials.identityToken,
                "refreshToken" to credentials.refreshToken,
                "profileUuid" to credentials.profileUuid,
                "profileName" to credentials.profileName,
                "expiresAt" to credentials.expiresAt,
            ),
        )
        try {
            Files.writeString(Path.of(CREDENTIALS_FILE), ConfigServiceRegistry["json"].saveIntoString(section))
        } catch (e: Exception) {
            logger().error("Error while saving credentials", e)
        }
    }

    /**
     * Refreshes the OAuth tokens with the saved refresh token.
     *
     * Note: each refresh **rotates** the refresh token (the previous one becomes invalid), so
     * the new one must be saved or the next refresh will fail.
     *
     * @return the new tokens, or `null` if the refresh failed.
     */
    fun refreshOAuthTokens(): OAuthTokens? {
        val current = load() ?: return null
        val refreshToken = current.refreshToken ?: return null

        val tokens = oauth.refreshTokens(refreshToken) ?: return null

        // The previous refresh token is no longer valid: the new one is saved.
        save(current.copy(refreshToken = tokens.refreshToken ?: refreshToken))
        return tokens
    }

    /**
     * Updates the saved tokens (after a game session refresh).
     *
     * If there were no saved credentials the file is not created: the tokens may come from
     * environment variables and there is nothing to save.
     */
    fun updateTokens(sessionToken: String, identityToken: String, expiresAt: String?) {
        val current = load() ?: return
        save(
            current.copy(
                sessionToken = sessionToken,
                identityToken = identityToken,
                expiresAt = expiresAt,
            ),
        )
    }

    fun newAudience(): String = UUID.randomUUID().toString()
}
