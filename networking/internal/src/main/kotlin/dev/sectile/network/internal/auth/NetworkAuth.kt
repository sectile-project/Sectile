package dev.sectile.network.internal.auth

import dev.sectile.kernel.logger.Logger
import dev.sectile.network.internal.auth.jwt.CertificateUtil
import dev.sectile.network.internal.auth.jwt.JWTValidator
import dev.sectile.network.internal.auth.jwt.JwksCache
import dev.sectile.network.internal.config.AuthConfig
import java.security.cert.X509Certificate
import java.time.Instant
import java.time.OffsetDateTime
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlin.math.max

/**
 * Authentication services shared by the server.
 *
 * It initializes when the network server starts. In insecure mode, there is no Session Service
 * or JWT validator: login is limited to the password challenge.
 */
object NetworkAuth {

    @Volatile
    private var sessionService: SessionServiceClient? = null

    @Volatile
    private var validator: JWTValidator? = null

    @Volatile
    private var jwksCache: JwksCache? = null

    /** Current server session token (Bearer for the Session Service). */
    @Volatile
    var sessionToken: String? = null
        private set

    /** Current server identity token (sent to the client in `AuthGrant`). */
    @Volatile
    var identityToken: String? = null
        private set

    /** Fingerprint of the server certificate (base64url SHA-256), used in token exchange. */
    @Volatile
    var serverCertificateFingerprint: String? = null
        private set

    private var refreshTask: ScheduledFuture<*>? = null
    private var expiresAt: Long = 0L

    private const val REFRESH_CHECK_SECONDS = 300L
    private const val REFRESH_WINDOW_SECONDS = 600L

    val isAuthenticated: Boolean
        get() = !sessionToken.isNullOrEmpty() && !identityToken.isNullOrEmpty()

    private val scheduler: ScheduledExecutorService by lazy {
        Executors.newSingleThreadScheduledExecutor { runnable ->
            val thread = Thread(runnable, "AuthRefresh")
            thread.isDaemon = true
            thread
        }
    }

    fun minutesUntilExpire(isoTime: String?): Long {
        if (isoTime == null) return 0L
        return max(0L, (OffsetDateTime.parse(isoTime).toInstant().toEpochMilli() - System.currentTimeMillis()) / 60_000L)
    }

    fun init(config: AuthConfig, serverCertificate: X509Certificate?, logger: Logger) {
        serverCertificateFingerprint = serverCertificate?.let { CertificateUtil.computeCertificateFingerprint(it) }
        sessionToken = config.serverSessionToken
        identityToken = config.serverIdentityToken

        if (!config.authenticated) {
            sessionService = null
            validator = null
            return
        }

        if (sessionToken.isNullOrEmpty() || identityToken.isNullOrEmpty()) {
            val cached = ServerAuthenticator.load()
            if (cached != null && cached.valid) {
                logger.info(
                    "Using cached server credentials for '${cached.profileName}' " +
                        "(expires in ${minutesUntilExpire(cached.expiresAt)} minutes)",
                )
                sessionToken = cached.sessionToken
                identityToken = cached.identityToken
                expiresAt = parseExpiry(cached.expiresAt)
            } else {
                if (cached != null) {
                    logger.warn("Cached server credentials have expired. Run '/auth login' to authenticate.")
                } else {
                    logger.warn("No server credentials found. Run '/auth login' to authenticate.")
                }
            }
        } else {
            expiresAt = parseExpiry(ServerAuthenticator.load()?.expiresAt)
        }

        val url = config.sessionServiceUrl ?: return
        val client = SessionServiceClient(url, config.httpTimeout, logger = logger)
        sessionService = client
        val cache = JwksCache(client)
        jwksCache = cache
        validator = JWTValidator(cache, url, config.audience, logger)

        if (!sessionToken.isNullOrEmpty()) {
            scheduleRefresh(logger)
        }
        prewarmJwks(logger)
    }

    fun updateCredentials(credentials: ServerCredentials, logger: Logger?) {
        sessionToken = credentials.sessionToken
        identityToken = credentials.identityToken
        expiresAt = parseExpiry(credentials.expiresAt)
        if (sessionService != null) {
            scheduleRefresh(logger)
        }
    }

    private fun prewarmJwks(logger: Logger?) {
        scheduler.execute {
            try {
                val set = jwksCache?.load()
                logger?.info("JWKS loaded (${set?.keys?.size ?: 0} keys)")
            } catch (e: Exception) {
                logger?.warn("Could not preload the JWKS: ${e.javaClass.simpleName}")
            }
        }
    }

    /** Session Service client, or `null` if the server is in insecure mode. */
    fun sessionService(): SessionServiceClient? = sessionService

    /** JWT validator, or `null` if the server is in insecure mode. */
    fun validator(): JWTValidator? = validator

    /**
     * Renews the game session before it expires.
     *
     * The game session has an expiration date: if it is not renewed, the server will stop
     * being able to authenticate. It is checked every few minutes and renewed when there is little time left.
     */
    private fun scheduleRefresh(logger: Logger?) {
        refreshTask?.cancel(false)
        refreshTask = scheduler.scheduleWithFixedDelay(
            {
                try {
                    refreshIfNeeded(logger)
                } catch (e: Exception) {
                    logger?.debug("Session refresh failed: ${e.javaClass.simpleName}")
                }
            },
            REFRESH_CHECK_SECONDS,
            REFRESH_CHECK_SECONDS,
            TimeUnit.SECONDS,
        )
    }

    private fun refreshIfNeeded(logger: Logger?) {
        val client = sessionService ?: return
        val current = sessionToken ?: return

        val now = Instant.now().epochSecond
        // The expiration is read from the token itself: it is the source of truth.
        val expiry = JwtClaims.expiresAt(current).let { if (it > 0) it else expiresAt }
        if (expiry > 0 && now < expiry - REFRESH_WINDOW_SECONDS) return

        val session = client.refreshGameSession(current)
        if (session != null) {
            sessionToken = session.sessionToken
            identityToken = session.identityToken
            expiresAt = parseExpiry(session.expiresAt)
            logger?.info("Game session refreshed (expires in ${minutesUntilExpire(session.expiresAt)} minutes)")
            ServerAuthenticator.updateTokens(session.sessionToken, session.identityToken, session.expiresAt)
            return
        }

        // The session cannot be refreshed (e.g., it has already expired): the OAuth token is renewed and
        // a new game session is created, as the official server does.
        logger?.warn("Could not refresh the game session, renewing the OAuth token instead")
        val oauthTokens = ServerAuthenticator.refreshOAuthTokens()
        val accessToken = oauthTokens?.accessToken
        if (accessToken == null) {
            logger?.error(
                "Could not renew the OAuth token: the server is no longer authenticated. " +
                    "Restart it to authenticate again.",
            )
            return
        }

        val profileUuid = ServerAuthenticator.load()?.profileUuid?.let {
            runCatching { UUID.fromString(it) }.getOrNull()
        }
        val newSession = if (profileUuid == null) {
            null
        } else {
            client.createGameSession(accessToken, profileUuid)
        }
        if (newSession == null) {
            logger?.error("Could not create a new game session: the server is no longer authenticated")
            return
        }

        sessionToken = newSession.sessionToken
        identityToken = newSession.identityToken
        expiresAt = parseExpiry(newSession.expiresAt)
        logger?.info("New game session created (expires in ${minutesUntilExpire(newSession.expiresAt)} minutes)")
        ServerAuthenticator.updateTokens(newSession.sessionToken, newSession.identityToken, newSession.expiresAt)
    }

    private fun parseExpiry(value: String?): Long {
        if (value.isNullOrEmpty()) return 0L
        return try {
            Instant.parse(value).epochSecond
        } catch (_: Exception) {
            0L
        }
    }

    /** Releases the services (when shutting down the server). */
    fun shutdown() {
        refreshTask?.cancel(false)
        refreshTask = null
        sessionService = null
        validator = null
        sessionToken = null
        identityToken = null
        serverCertificateFingerprint = null
    }
}
