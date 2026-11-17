package dev.sectile.network.internal.io

import dev.sectile.kernel.logger.Logger
import dev.sectile.network.api.packet.PacketRegistry
import dev.sectile.network.api.packet.handler.PacketHandlerStorage
import dev.sectile.network.api.player.connection.ConnectionStage
import dev.sectile.network.internal.io.frame.PacketFramer
import dev.sectile.network.internal.quic.QuicApplicationErrorCode
import dev.sectile.network.internal.quic.QuicClose
import dev.sectile.network.internal.quic.QuicProtocol
import dev.sectile.network.protocol.data.DataSize
import io.netty.buffer.ByteBuf
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.ByteToMessageDecoder
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

private const val HEADER_LENGTH =
    DataSize.INT + // Payload length
        DataSize.INT // Packet id

private const val CHECK_INTERVAL_MS = 1000L

/**
 * Protocol frame decoder. Wire format: `[uint32 LE payloadLength][uint32 LE packetId][payload]`.
 *
 * Enforces:
 *  - read timeouts measured since the last complete packet (ignores trickle bytes against slowloris),
 *  - fail-fast validation order: length -> registry id -> max packet size -> stream channel ->
 *    stage handler acceptance -> complete payload -> rate limit,
 *  - strict stage enforcement: unexpected packets in strict stages close the QUIC connection with
 *    `PROTOCOL_VIOLATION`, whereas non-strict stages skip unhandled payloads without decompressing,
 *  - silent QUIC termination on malformed frames.
 */
class PacketDecoder(
    private val logger: Logger,
    private val stageProvider: (() -> Pair<ConnectionStage, PacketHandlerStorage>)? = null,
) : ByteToMessageDecoder() {

    private var lastPacketTimeNanos = 0L
    private var timeoutCheckFuture: ScheduledFuture<*>? = null

    override fun handlerAdded(ctx: ChannelHandlerContext) {
        if (ctx.channel().isActive) {
            initialize(ctx)
        }
        super.handlerAdded(ctx)
    }

    override fun channelActive(ctx: ChannelHandlerContext) {
        initialize(ctx)
        super.channelActive(ctx)
    }

    override fun channelInactive(ctx: ChannelHandlerContext) {
        cancelTimeoutCheck()
        super.channelInactive(ctx)
    }

    override fun handlerRemoved0(ctx: ChannelHandlerContext) {
        cancelTimeoutCheck()
    }

    private fun initialize(ctx: ChannelHandlerContext) {
        if (timeoutCheckFuture != null) return
        lastPacketTimeNanos = System.nanoTime()
        timeoutCheckFuture = ctx.executor().scheduleAtFixedRate(
            { checkTimeout(ctx) },
            CHECK_INTERVAL_MS,
            CHECK_INTERVAL_MS,
            TimeUnit.MILLISECONDS,
        )
    }

    private fun cancelTimeoutCheck() {
        timeoutCheckFuture?.cancel(false)
        timeoutCheckFuture = null
    }

    private fun checkTimeout(ctx: ChannelHandlerContext) {
        val channel = ctx.channel()
        if (!channel.isActive) {
            cancelTimeoutCheck()
            return
        }

        val timeout = channel.attr(ProtocolUtil.PACKET_TIMEOUT_KEY).get() ?: return

        val elapsedNanos = System.nanoTime() - lastPacketTimeNanos
        if (elapsedNanos >= timeout.toNanos()) {
            cancelTimeoutCheck()
            logger.warn("Packet read timeout after ${timeout.toMillis()} ms")
            QuicClose.application(channel, QuicApplicationErrorCode.Timeout)
        }
    }

    override fun decode(ctx: ChannelHandlerContext, buf: ByteBuf, out: MutableList<Any>) {
        if (buf.readableBytes() < HEADER_LENGTH) {
            return
        }

        buf.markReaderIndex()
        val payloadLength = buf.readIntLE()

        // 1. Validate declared frame length upfront.
        if (payloadLength < 0 || payloadLength > QuicProtocol.MAX_PACKET_SIZE) {
            logger.warn("Rejected packet: invalid length $payloadLength")
            reject(ctx.channel(), buf)
            return
        }

        val packetId = buf.readIntLE()

        // 2. Ensure the packet id is a valid client-to-server protocol entry.
        val definition = PacketRegistry.getToServer(packetId)
        if (definition == null) {
            logger.warn("Rejected packet: unknown id $packetId")
            reject(ctx.channel(), buf)
            return
        }

        // 3. Enforce per-packet protocol size bound.
        if (payloadLength > definition.maxSize) {
            logger.warn("Rejected packet ${definition.name}: length $payloadLength > ${definition.maxSize}")
            reject(ctx.channel(), buf)
            return
        }

        // 4. Enforce stream channel affinity if pinned on the stream.
        val streamChannel = ctx.channel().attr(ProtocolUtil.STREAM_CHANNEL_KEY).get()
        if (streamChannel != null && streamChannel != definition.channel) {
            logger.warn("Rejected packet ${definition.name}: channel $streamChannel != ${definition.channel}")
            reject(ctx.channel(), buf)
            return
        }

        // 5. Check whether the active stage (or per-stream override) accepts this packet.
        val handlesPacket = ctx.channel().attr(ProtocolUtil.HANDLES_PACKET_KEY).get()
        val stagePair = stageProvider?.invoke()
        val hasHandler = if (handlesPacket != null) {
            handlesPacket.test(packetId)
        } else {
            stagePair?.second?.hasHandlers(packetId) ?: true
        }
        val strict = handlesPacket != null || (stagePair?.first?.strict ?: true)

        if (!hasHandler && strict) {
            logger.warn("Rejected packet ${definition.name}: not accepted in stage ${stagePair?.first}")
            reject(ctx.channel(), buf)
            return
        }

        // 6. Wait for full payload bytes before consuming rate limit tokens or decompressing.
        if (buf.readableBytes() < payloadLength) {
            buf.resetReaderIndex()
            return
        }

        // 7. Apply connection-wide rate limit shared across all QUIC streams.
        val bucket = QuicClose.quicChannelOf(ctx.channel())?.attr(ProtocolUtil.RATE_LIMIT_KEY)?.get()
        if (bucket != null && !bucket.tryConsume(System.nanoTime())) {
            logger.warn("Rate limit exceeded, disconnecting")
            buf.skipBytes(buf.readableBytes())
            QuicClose.application(ctx.channel(), QuicApplicationErrorCode.RateLimited)
            return
        }

        // Skip payloads with no registered handler in non-strict stages without decompressing.
        if (!hasHandler) {
            buf.skipBytes(payloadLength)
            lastPacketTimeNanos = System.nanoTime()
            logger.debug("Ignoring unhandled packet ${definition.name} (#$packetId) in stage ${stagePair?.first}")
            return
        }

        try {
            val payload = PacketFramer.extractPayload(buf, definition, payloadLength)
            out.add(DecodedPacket(definition, payload))
            lastPacketTimeNanos = System.nanoTime()
        } catch (e: Exception) {
            logger.warn("Malformed packet id=$packetId: ${e.javaClass.simpleName}: ${e.message}")
            reject(ctx.channel(), buf)
        }
    }

    private fun reject(channel: Channel, buf: ByteBuf) {
        buf.skipBytes(buf.readableBytes())
        QuicClose.protocolViolation(channel)
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        // Omit stack traces so remote clients cannot flood disk/logs.
        logger.warn("Unhandled exception: ${cause.javaClass.simpleName}: ${cause.message}")
        QuicClose.protocolViolation(ctx.channel())
    }
}
