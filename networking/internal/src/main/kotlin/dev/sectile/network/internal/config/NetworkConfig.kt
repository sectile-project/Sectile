package dev.sectile.network.internal.config

import java.time.Duration

class NetworkConfig(
    val host: String,
    val port: Int,

    val threadCount: Int,

    val zstdCompressionLevel: Int,
    val readTimeoutSeconds: Int,
    val playTimeoutSeconds: Int,

    val reuseAddress: Boolean,

    val rateLimitBurst: Int,
    val rateLimitPacketsPerSecond: Int,

    val serverName: String,
    val motd: String,
    val maxPlayers: Int,

    val auth: AuthConfig,
) {

    val rateLimitEnabled: Boolean
        get() = rateLimitBurst > 0 && rateLimitPacketsPerSecond > 0

    val readTimeout: Duration
        get() = Duration.ofSeconds(readTimeoutSeconds.toLong())
}
