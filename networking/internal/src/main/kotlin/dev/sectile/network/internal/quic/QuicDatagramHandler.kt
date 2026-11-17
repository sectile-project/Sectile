package dev.sectile.network.internal.quic

import dev.sectile.kernel.logger.Logger
import dev.sectile.network.internal.NetworkInternalConnector
import dev.sectile.network.internal.player.PlayerConnectionInitializer
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.ChannelInitializer
import io.netty.handler.codec.quic.QuicChannel
import io.netty.handler.codec.quic.QuicCongestionControlAlgorithm
import io.netty.handler.codec.quic.QuicServerCodecBuilder
import io.netty.handler.codec.quic.QuicSslContext
import java.util.concurrent.TimeUnit

internal class QuicDatagramHandler(
    private val sslContext: QuicSslContext,
    private val logger: Logger,
) : ChannelInboundHandlerAdapter() {

    override fun channelActive(ctx: ChannelHandlerContext) {
        super.channelActive(ctx)

        val quicCodec = QuicServerCodecBuilder()
            .sslContext(sslContext)
            // The original server does not use tokens (no Retry): NoQuicTokenHandler avoids the extra RTT.
            .tokenHandler(NoQuicTokenHandler.INSTANCE)
            .maxIdleTimeout(NetworkInternalConnector.config.readTimeoutSeconds.toLong(), TimeUnit.SECONDS)
            .ackDelayExponent(3)
            // Stream windows and limits as in the original server's initConfig/applyTransportConfig.
            .initialMaxData(QuicProtocol.INITIAL_CONNECTION_WINDOW)
            .initialMaxStreamDataBidirectionalLocal(QuicProtocol.INITIAL_STREAM_WINDOW)
            .initialMaxStreamDataBidirectionalRemote(QuicProtocol.INITIAL_STREAM_WINDOW)
            .initialMaxStreamDataUnidirectional(QuicProtocol.INITIAL_UNI_STREAM_WINDOW)
            .initialMaxStreamsBidirectional(QuicProtocol.MAX_BIDI_STREAMS)
            .initialMaxStreamsUnidirectional(QuicProtocol.MAX_UNI_STREAMS)
            // disable_active_migration
            .activeMigration(false)
            // PMTU discovery. maxSendUdpPayloadSize is not set: Netty's send loop
            // cannot discard the EMSGSIZE of a probe that exceeds the MTU.
            .discoverPmtu(true)
            // The original server uses "bbr2_gcongestion", which Netty does not expose; BBR is the closest.
            .congestionControlAlgorithm(QuicCongestionControlAlgorithm.BBR)
            // ChannelInitializer: a new handler instance per connection (a stateful handler cannot be shared).
            .handler(object : ChannelInitializer<QuicChannel>() {
                override fun initChannel(ch: QuicChannel) {
                    ch.pipeline().addLast(QuicConnectionHandler(logger))
                }
            })
            .streamHandler(PlayerConnectionInitializer(logger))
            .build()

        ctx.channel().pipeline().addLast(quicCodec)

        logger.info("initialized for channel: ${ctx.channel().localAddress()}")
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        logger.error("Error in Datagram Handler", cause)
        ctx.close()
    }
}
