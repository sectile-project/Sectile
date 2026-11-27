package dev.sectile.network.internal

import dev.sectile.kernel.config.ConfigServiceRegistry
import dev.sectile.network.internal.config.AuthConfig
import dev.sectile.network.internal.config.AuthMode
import dev.sectile.network.internal.config.NetworkConfig
import dev.sectile.sdk.bundled.config.json.JsonConfigService
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

/**
 * The QUIC transport starts in the background: if it fails (certificate, event loop,
 * SSL certificate) the server appears to start but never accepts connections.
 */
class TransportStartupTest {

    @Test
    fun `the quic transport starts`() {
        val config = NetworkConfig(
            host = "127.0.0.1",
            port = 0,
            threadCount = 1,
            zstdCompressionLevel = 3,
            readTimeoutSeconds = 30,
            playTimeoutSeconds = 120,
            reuseAddress = true,
            rateLimitBurst = 0,
            rateLimitPacketsPerSecond = 0,
            serverName = "test",
            motd = "test",
            maxPlayers = 10,
            auth = AuthConfig(AuthMode.INSECURE, null, null, null, null, null),
        )
        ConfigServiceRegistry.register(JsonConfigService)

        // If startup fails, the future fails with the actual exception.
        NetworkInternal.init(config, dev.sectile.network.internal.auth.TestLogger()).get(60, TimeUnit.SECONDS)
        assertTrue(true)
        NetworkInternal.shutdown()
    }
}
