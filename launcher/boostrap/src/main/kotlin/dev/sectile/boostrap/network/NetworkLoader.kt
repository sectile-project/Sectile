package dev.sectile.boostrap.network

import dev.sectile.kernel.Kernel
import dev.sectile.kernel.config.ConfigSection
import dev.sectile.kernel.config.ConfigService
import dev.sectile.kernel.logger.Logger
import dev.sectile.network.internal.NetworkInternal
import dev.sectile.network.internal.config.AuthConfig
import dev.sectile.network.internal.config.AuthMode
import dev.sectile.network.internal.config.NetworkConfig
import dev.sectile.sdk.util.TimeFormatter
import java.util.UUID
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
    NetworkInternal.init(networkConfig, transportLogger).whenComplete { _: Any?, error: Throwable? ->
        if (error != null) {
            logger.error("Could not start the network transport", error)
        } else {
            logger.info("Server networking started")
        }
    }

    Kernel.addStopTask { NetworkInternal.shutdown() }
}

private fun loadAuth(section: ConfigSection, logger: Logger): AuthConfig {
    val mode = section.getEnum("mode", AuthMode.AUTHENTICATED)

    val auth = AuthConfig(
        mode = mode,
        sessionServiceUrl = section.getString("session-service-url") ?: "https://sessions.hytale.com",
        audience = UUID.randomUUID().toString(),
        serverSessionToken = null,
        serverIdentityToken = null,
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
        logger.warn("Authentication disabled (auth.mode=insecure)")
    }

    return auth
}
