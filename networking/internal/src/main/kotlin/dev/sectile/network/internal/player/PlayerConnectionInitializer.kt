package dev.sectile.network.internal.player

import dev.sectile.kernel.Kernel
import dev.sectile.kernel.logger.Logger
import dev.sectile.network.internal.NetworkInternalConnector
import dev.sectile.network.internal.io.DecodedPacket
import dev.sectile.network.internal.io.PacketDecoder
import dev.sectile.network.internal.io.PacketEncoder
import dev.sectile.network.internal.io.ProtocolUtil
import dev.sectile.network.internal.io.StreamRole
import dev.sectile.network.internal.packet.PacketInboundHandler
import dev.sectile.network.internal.packet.buffer.NettyReadBuffer
import dev.sectile.network.internal.quic.QuicApplicationErrorCode
import dev.sectile.network.internal.quic.QuicClose
import dev.sectile.network.internal.quic.QuicProtocol
import dev.sectile.network.internal.quic.QuicTransport
import dev.sectile.network.protocol.ProtocolPacketTable
import dev.sectile.network.protocol.connection.ServerDisconnectPacket
import dev.sectile.network.protocol.enums.DisconnectType
import dev.sectile.network.protocol.stream.StreamOpenPacket
import dev.sectile.network.protocol.stream.StreamOpenResponsePacket
import dev.sectile.network.protocol.structures.FormattedMessage
import io.netty.channel.Channel
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.ChannelInitializer
import io.netty.handler.codec.quic.QuicStreamChannel
import io.netty.handler.codec.quic.QuicStreamPriority
import io.netty.util.ReferenceCountUtil
import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.function.IntPredicate

/**
 * Configures each QUIC stream opened by a connected client:
 *  - stream 0           -> primary stream (Default), urgency 0 non-incremental
 *  - client bidi stream -> auxiliary stream (voice), only when an auxiliary handler factory is registered
 *  - anything else      -> rejected
 */
class PlayerConnectionInitializer(private val logger: Logger) : ChannelInitializer<Channel>() {

    override fun initChannel(ch: Channel) {
        if (ch !is QuicStreamChannel) {
            ch.close()
            return
        }

        val quic = ch.parent()
        val streamId = ch.streamId()

        // Rejected ALPN version: send ServerDisconnect without decoder and close QUIC shortly after.
        val rejectCode = quic.attr(QuicTransport.ALPN_REJECT_ERROR_CODE_ATTR).get()
        if (rejectCode != null) {
            initRejected(ch, rejectCode)
            return
        }

        val clientCert = quic.attr(QuicTransport.CLIENT_CERTIFICATE_ATTR).get()
        if (clientCert == null) {
            logger.warn("Stream $streamId without client certificate, closing")
            ch.close()
            return
        }
        ch.attr(QuicTransport.CLIENT_CERTIFICATE_ATTR).set(clientCert)

        when {
            streamId == 0L -> initPrimary(ch)

            (streamId and 0x3L) == 0L -> initAuxiliary(ch)

            else -> {
                logger.warn("Unexpected stream type for stream $streamId, closing")
                ch.close()
            }
        }
    }

    private fun initRejected(ch: QuicStreamChannel, code: QuicApplicationErrorCode) {
        ch.config().isAutoRead = false
        ch.pipeline().addLast("encoder", PacketEncoder.INSTANCE)

        val reason = "client.general.disconnect.clientOutdated"
        val disconnect = ServerDisconnectPacket(
            reason = FormattedMessage().also { it.rawText = reason },
            type = DisconnectType.DISCONNECT,
        )
        ch.writeAndFlush(disconnect).addListener {
            ch.eventLoop().schedule(
                { QuicClose.application(ch, code, DisconnectSupport.reasonEncoder?.invoke(reason)) },
                100,
                TimeUnit.MILLISECONDS,
            )
        }
    }

    private fun initPrimary(ch: QuicStreamChannel) {
        val quic = ch.parent()
        val connLogger = Kernel.loggerFactory.createLogger(quic.remoteSocketAddress().toString())
        val connection = NettyPlayerConnection(ch, connLogger, NetworkInternalConnector.stageStorages)
        quic.attr(NettyPlayerConnection.PRIMARY_CONNECTION_KEY).set(connection)

        ch.attr(ProtocolUtil.STREAM_ROLE_KEY).set(StreamRole.DEFAULT)
        connection.setReadTimeout(Duration.ofSeconds(NetworkInternalConnector.config.readTimeoutSeconds.toLong()))

        ch.pipeline()
            .addLast("decoder", PacketDecoder(connLogger) { connection.stage to connection.currentStageStorage })
            .addLast("encoder", PacketEncoder(connLogger))
            .addLast("packet_handler", PacketInboundHandler(connection))

        ch.eventLoop().execute { ch.updatePriority(QuicStreamPriority(0, false)) }

        connLogger.info("Primary stream initialized.")
    }

    private fun initAuxiliary(ch: QuicStreamChannel) {
        val quic = ch.parent()
        val factory = QuicAuxStreams.factory
        if (factory == null) {
            logger.warn("No aux stream handler configured, closing stream ${ch.streamId()}")
            ch.close()
            return
        }

        val primary = quic.attr(NettyPlayerConnection.PRIMARY_CONNECTION_KEY).get()
        if (primary == null) {
            logger.warn("Primary stream not ready, closing aux stream ${ch.streamId()}")
            ch.close()
            return
        }

        ch.attr(ProtocolUtil.STREAM_ROLE_KEY).set(StreamRole.VOICE_AUX)
        ch.attr(ProtocolUtil.PACKET_TIMEOUT_KEY).set(QuicProtocol.AUX_STREAM_PENDING_TIMEOUT)

        ch.pipeline()
            .addLast("decoder", PacketDecoder(logger))
            .addLast("encoder", PacketEncoder.INSTANCE)
            .addLast("pending_stream", PendingAuxStreamHandler(primary, factory, logger))
    }
}

/**
 * Pipeline handler for client-initiated auxiliary streams awaiting `StreamOpen`.
 */
class PendingAuxStreamHandler(
    private val connection: NettyPlayerConnection,
    private val factory: (NettyPlayerConnection, QuicStreamChannel) -> ChannelHandler?,
    private val logger: Logger,
) : ChannelInboundHandlerAdapter() {

    override fun handlerAdded(ctx: ChannelHandlerContext) {
        ctx.channel().attr(ProtocolUtil.HANDLES_PACKET_KEY)
            .set(IntPredicate { it == ProtocolPacketTable.STREAM_OPEN.id })
        super.handlerAdded(ctx)
    }

    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        if (msg !is DecodedPacket || msg.definition.id != ProtocolPacketTable.STREAM_OPEN.id) {
            ReferenceCountUtil.release(msg)
            logger.warn("Auxiliary stream first packet was not StreamOpen, closing")
            ctx.close()
            return
        }

        val packet = StreamOpenPacket()
        try {
            packet.read(NettyReadBuffer(msg.payload))
        } finally {
            msg.release()
        }

        val streamChannel = ctx.channel() as? QuicStreamChannel
        val handler = if (streamChannel != null) factory(connection, streamChannel) else null
        if (handler == null) {
            logger.warn("Aux stream ${streamChannel?.streamId()} rejected by handler")
            ctx.writeAndFlush(
                StreamOpenResponsePacket(
                    type = packet.type,
                    accepted = false,
                    rejectionReason = "Stream type not supported",
                ),
            ).addListener { ctx.close() }
            return
        }

        ctx.pipeline().replace(this, "aux_handler", handler)
        ctx.writeAndFlush(
            StreamOpenResponsePacket(
                type = packet.type,
                accepted = true,
                rejectionReason = null,
            ),
        )
    }
}

/**
 * Factory for auxiliary QUIC stream handlers. Returning `null` rejects the auxiliary stream.
 */
object QuicAuxStreams {
    @Volatile
    var factory: ((NettyPlayerConnection, QuicStreamChannel) -> ChannelHandler?)? = null
}
