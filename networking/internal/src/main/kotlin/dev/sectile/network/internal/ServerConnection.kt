package dev.sectile.network.internal

import dev.sectile.kernel.logger.Logger
import dev.sectile.network.internal.auth.NetworkAuth
import dev.sectile.network.internal.config.NetworkConfig
import dev.sectile.network.internal.quic.QuicTransport
import io.netty.channel.EventLoopGroup
import io.netty.channel.MultiThreadIoEventLoopGroup
import java.util.concurrent.CompletableFuture

internal class ServerConnection {

    @Volatile
    private var transport: QuicTransport? = null

    fun init(config: NetworkConfig, logger: Logger): CompletableFuture<Unit> {
        val sslFuture = CompletableFuture.supplyAsync { QuicTransport.createSslContext() }
        val groupFuture = CompletableFuture.supplyAsync { createEventLoop(config.threadCount) }

        return sslFuture
            .thenCombine(groupFuture) { ssl, group -> QuicTransport(group, ssl, logger) }
            .thenCompose { quic ->
                transport = quic
                NetworkAuth.init(config.auth, QuicTransport.serverCertificate, logger)
                quic.bind(config)
            }
    }

    fun shutdown(): CompletableFuture<Unit> {
        NetworkAuth.shutdown()
        return transport?.shutdown() ?: CompletableFuture.completedFuture(Unit)
    }

    private fun createEventLoop(expectedThreads: Int): EventLoopGroup {
        val threads = if (expectedThreads <= 0) Runtime.getRuntime().availableProcessors() else expectedThreads
        return MultiThreadIoEventLoopGroup(threads, NativeTransport.current.ioHandlerFactory())
    }
}
