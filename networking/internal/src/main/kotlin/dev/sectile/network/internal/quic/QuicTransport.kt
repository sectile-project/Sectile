package dev.sectile.network.internal.quic

import dev.sectile.kernel.logger.Logger
import dev.sectile.network.internal.NativeTransport
import dev.sectile.network.internal.config.NetworkConfig
import dev.sectile.network.internal.quic.certificate.RuntimeCertificateProvider
import io.netty.bootstrap.Bootstrap
import io.netty.channel.Channel
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelOption
import io.netty.channel.EventLoopGroup
import io.netty.channel.socket.DatagramChannel
import io.netty.handler.codec.quic.QuicSslContext
import io.netty.handler.codec.quic.QuicSslContextBuilder
import io.netty.handler.ssl.ClientAuth
import io.netty.handler.ssl.util.InsecureTrustManagerFactory
import io.netty.util.AttributeKey
import java.net.InetSocketAddress
import java.nio.file.Path
import java.security.cert.X509Certificate
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

class QuicTransport internal constructor(
    private val workerGroup: EventLoopGroup,
    private val sslContext: QuicSslContext,
    private val logger: Logger,
) {

    companion object {
        val CLIENT_CERTIFICATE_ATTR: AttributeKey<X509Certificate> =
            AttributeKey.valueOf("CLIENT_CERTIFICATE")

        /**
         * Application error code with which a connection rejected due to its
         * ALPN version is closed. The stream reads it to send `ServerDisconnect` before
         * closing.
         */
        val ALPN_REJECT_ERROR_CODE_ATTR: AttributeKey<QuicApplicationErrorCode> =
            AttributeKey.valueOf("ALPN_REJECT_ERROR_CODE")

        /**
         * Server certificate.
         *
         * Used for mutual binding: its base64url-encoded SHA-256 fingerprint is sent to the Session
         * Service, and the token it returns is bound to it.
         */
        @Volatile
        lateinit var serverCertificate: X509Certificate
            private set

        internal fun createSslContext(): QuicSslContext {
            val bundle = RuntimeCertificateProvider.loadOrCreate(
                Path.of("certs/cert.pem"),
                Path.of("certs/key.pem"),
            )
            serverCertificate = bundle.certificate
            return QuicSslContextBuilder.forServer(bundle.privateKey, null, bundle.certificate)
                .applicationProtocols(*QuicProtocol.alpnProtocols())
                // 0-RTT disabled: it is not forward-secret and is vulnerable to replays.
                .earlyData(false)
                // mTLS: the client must present a certificate.
                .clientAuth(ClientAuth.REQUIRE)
                // Any client certificate is accepted: real validation is done via the
                // token binding (cnf.x5t#S256), not via a trust chain.
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .build()
        }
    }

    // Listeners run on event loop threads, not the one calling bind()
    private val activeChannels = CopyOnWriteArrayList<Channel>()

    fun bind(config: NetworkConfig): CompletableFuture<Unit> {
        val bootstrap = Bootstrap()
            .group(workerGroup)
            .channel(NativeTransport.current.datagramChannel)
            .option(ChannelOption.SO_REUSEADDR, config.reuseAddress)
            .option(ChannelOption.SO_RCVBUF, QuicProtocol.SOCKET_RECEIVE_BUFFER_SIZE)
            .handler(object : ChannelInitializer<DatagramChannel>() {
                override fun initChannel(ch: DatagramChannel) {
                    ch.pipeline().addLast(QuicDatagramHandler(sslContext, logger))
                }
            })

        val host = config.host
        val port = config.port
        val bindAll = host == "0.0.0.0" || host == "::0" || host.isEmpty()

        if (!bindAll) {
            return bindAsync(bootstrap, host, port)
                .thenApply { logger.info("QUIC listening on specific host: $host:$port") }
                .exceptionally { throw IllegalStateException("Failed to bind to specific host $host:$port", it.unwrap()) }
        }

        // Both binds are launched at the same time; an individual failure is only logged
        val v4 = bindAsync(bootstrap, "0.0.0.0", port).handle { _, err ->
            if (err != null) {
                logger.warn("Could not bind IPv4: ${err.unwrap().message}")
            } else {
                logger.info("listening on IPv4: 0.0.0.0:$port")
            }
        }
        val v6 = bindAsync(bootstrap, "::0", port).handle { _, err ->
            if (err != null) {
                logger.warn("Could not bind IPv6 (might be covered by dual-stack OS): ${err.unwrap().message}")
            } else {
                logger.info("listening on IPv6: [::]:$port")
            }
        }

        return v4.thenCombine(v6) { a, b ->
            if (a == null && b == null) {
                throw IllegalStateException("Failed to bind on any protocol (IPv4 or IPv6) on port $port")
            }
        }
    }

    fun shutdown(): CompletableFuture<Unit> {
        logger.info("Shutting down")
        val closes = activeChannels.map { it.close().toCompletable() }
        activeChannels.clear()

        return CompletableFuture.allOf(*closes.toTypedArray()).handle { _, _ -> }
            .thenCompose {
                val done = CompletableFuture<Unit>()
                workerGroup.shutdownGracefully(0L, 1L, TimeUnit.SECONDS)
                    .addListener { done.complete(Unit) }
                done
            }
    }

    private fun bindAsync(bootstrap: Bootstrap, host: String, port: Int): CompletableFuture<Channel> {
        val result = CompletableFuture<Channel>()
        bootstrap.bind(InetSocketAddress(host, port)).addListener { f ->
            val cf = f as ChannelFuture
            if (cf.isSuccess) {
                activeChannels.add(cf.channel())
                result.complete(cf.channel())
            } else {
                result.completeExceptionally(cf.cause())
            }
        }
        return result
    }

    private fun ChannelFuture.toCompletable(): CompletableFuture<Unit> {
        val r = CompletableFuture<Unit>()
        addListener { if (it.isSuccess) r.complete(Unit) else r.completeExceptionally(it.cause()) }
        return r
    }

    private fun Throwable.unwrap(): Throwable = (this as? CompletionException)?.cause ?: this
}
