package dev.sectile.boostrap.network

import dev.sectile.kernel.Kernel
import dev.sectile.kernel.config.ConfigSection
import dev.sectile.kernel.config.ConfigService
import dev.sectile.kernel.logger.Logger
import dev.sectile.network.internal.NetworkInternalConnector
import dev.sectile.network.internal.auth.ServerAuthenticator
import dev.sectile.network.internal.config.AuthConfig
import dev.sectile.network.internal.config.AuthMode
import dev.sectile.network.internal.config.NetworkConfig
import dev.sectile.sdk.util.TimeFormatter
import kotlin.math.max
import kotlin.math.min

fun loadNetwork(configService: ConfigService, logger: Logger) {
    val config = configService.createIfAbsentAndLoad("network")
    val rateLimitSection = config.getSection("rate-limit")
    val authSection = config.getSection("auth")

    val auth = loadAuth(authSection, logger)

    val networkConfig = NetworkConfig(
        config.getString("host", "0.0.0.0"),
        config.getInt("port", 5520),
        config.getInt("threads-to-use", 0),
        min(22, max(-1, config.getInt("zstd-compression-level", 3))),
        TimeFormatter.parseToSeconds(config.getString("read-timeout", "30s")).toInt(),
        TimeFormatter.parseToSeconds(config.getString("play-timeout", "120s")).toInt(),
        config.getBoolean("reuse-address", true),
        rateLimitSection.getInt("burst", 500),
        rateLimitSection.getInt("packets-per-second", 2000),
        config.getString("server-name", "Sectile"),
        config.getString("motd", "A Sectile server"),
        config.getInt("max-players", 100),
        auth,
    )

    val transportLogger = Kernel.loggerFactory.createLogger("QuicTransport")
    NetworkInternalConnector.init(networkConfig, transportLogger).whenComplete { _: Any?, error: Throwable? ->
        if (error != null) {
            logger.error("Could not start the network transport", error)
        } else {
            logger.info("Server networking started")
        }
    }

    Kernel.addStopTask { NetworkInternalConnector.shutdown() }
}

private fun loadAuth(section: ConfigSection, logger: Logger): AuthConfig {
    val mode = section.getEnum("mode", AuthMode.AUTHENTICATED)

    var sessionToken: String? = null
    var identityToken: String? = null

    if (mode == AuthMode.AUTHENTICATED) {
        val cached = ServerAuthenticator.load()
        if (cached != null && cached.valid) {
            logger.info(
                "Using cached server credentials for '${cached.profileName}' " +
                    "(expires at ${cached.expiresAt})",
            )
            sessionToken = cached.sessionToken
            identityToken = cached.identityToken
        } else {
            if (cached != null) {
                logger.warn("The cached server credentials have expired, authenticating again")
            }
            val credentials = ServerAuthenticator.authenticate(logger)
            if (credentials == null) {
                logger.error("Server authentication failed")
                throw IllegalStateException(
                    "Server authentication failed. Set HYTALE_SERVER_SESSION_TOKEN and " +
                        "HYTALE_SERVER_IDENTITY_TOKEN, or set auth.mode to 'insecure'.",
                )
            }
            sessionToken = credentials.sessionToken
            identityToken = credentials.identityToken
        }
    }

    val audience = ServerAuthenticator.newAudience()

    val auth = AuthConfig(
        mode = mode,
        sessionServiceUrl = section.getString("session-service-url") ?: "https://sessions.hytale.com",
        audience = audience,
        serverSessionToken = sessionToken,
        serverIdentityToken = identityToken,
        password = section.getString("password"),
    )

    val problem = auth.validate()
    if (problem != null) {
        logger.error("Invalid authentication configuration: $problem")
        throw IllegalStateException("Invalid authentication configuration: $problem")
    }

    if (auth.authenticated) {
        logger.info("Authentication enabled (session service: ${auth.sessionServiceUrl})")
    } else {
        logger.warn(
            "Authentication disabled: the official Hytale client refuses to enter insecure servers",
        )
    }

    return auth
}
