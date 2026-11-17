package dev.sectile.network.internal.io

import dev.sectile.kernel.logger.Logger
import dev.sectile.network.api.packet.OutboundPacket
import dev.sectile.network.internal.io.frame.PacketFramer
import dev.sectile.network.protocol.data.DataSize
import io.netty.buffer.ByteBuf
import io.netty.buffer.PooledByteBufAllocator
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.MessageToMessageEncoder

private const val DEFAULT_COMPRESSED_HINT = 256

/**
 * Netty outbound encoder that frames and optionally compresses [OutboundPacket] instances.
 */
@ChannelHandler.Sharable
class PacketEncoder(private val logger: Logger? = null) : MessageToMessageEncoder<OutboundPacket>() {

    companion object {
        /** Shared stateless encoder instance. */
        @JvmField
        val INSTANCE = PacketEncoder()

        /**
         * Estimates initial buffer capacity. Compressed packets avoid allocating `maxSize` upfront
         * because [PacketFramer] ensures exact `Zstd.compressBound` capacity after serializing.
         */
        fun estimatePacketSize(packet: OutboundPacket): Int {
            val size = packet.size()
            val payloadHint = if (size > 0) {
                size
            } else if (packet.definition.compressed) {
                DEFAULT_COMPRESSED_HINT
            } else {
                0
            }
            return DataSize.INT + DataSize.INT + payloadHint
        }

        fun allocateBuf(size: Int): ByteBuf? = PooledByteBufAllocator.DEFAULT.ioBuffer(size)
        fun allocateBuf(packet: OutboundPacket): ByteBuf? = PooledByteBufAllocator.DEFAULT.ioBuffer(estimatePacketSize(packet))
        fun allocateBuf(packet: OutboundPacket, ctx: ChannelHandlerContext): ByteBuf? = ctx.alloc().ioBuffer(estimatePacketSize(packet))
    }

    override fun encode(ctx: ChannelHandlerContext, packet: OutboundPacket, out: MutableList<Any>) {
        val buf = allocateBuf(packet, ctx) ?: return
        try {
            PacketFramer.writeFramedPacket(packet, buf)
            logger?.info("Encoded packet #${packet.definition.id} (${buf.readableBytes()} bytes)")
            out.add(buf)
        } catch (t: Throwable) {
            // Release buffer immediately if serialization fails or Zstd throws.
            buf.release()
            logger?.warn("Could not encode packet #${packet.definition.id}: ${t.javaClass.simpleName}: ${t.message}")
            throw t
        }
    }
}
