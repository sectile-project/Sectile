package dev.sectile.network.internal.io.frame

import com.github.luben.zstd.Zstd
import dev.sectile.network.api.packet.OutboundPacket
import dev.sectile.network.api.packet.PacketDefinition
import dev.sectile.network.internal.io.ProtocolException
import dev.sectile.network.internal.io.compression.PacketCompression
import dev.sectile.network.internal.packet.buffer.NettyWriteBuffer
import dev.sectile.network.internal.quic.QuicProtocol
import dev.sectile.network.protocol.DataSize
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled

/**
 * Reads and writes Hytale framed packets: `[uint32 LE payloadLength][uint32 LE packetId][payload]`.
 */
object PacketFramer {

    private const val PAYLOAD_FIELD_SIZE = DataSize.INT
    private const val DEFAULT_INITIAL_CAPACITY = 256

    fun writeFramedPacket(
        packet: OutboundPacket,
        out: ByteBuf,
    ) {
        val definition = packet.definition
        val lengthIndex = out.writerIndex()

        out.writeZero(PAYLOAD_FIELD_SIZE)
        out.writeIntLE(definition.id)

        val payloadStart = out.writerIndex()

        if (!definition.compressed) {
            packet.write(NettyWriteBuffer(out))

            val payloadSize = out.writerIndex() - payloadStart
            if (payloadSize > definition.maxSize || payloadSize > QuicProtocol.MAX_PACKET_SIZE) {
                out.writerIndex(lengthIndex)
                throw ProtocolException("Packet too large: $payloadSize > ${definition.maxSize}")
            }
            out.setIntLE(lengthIndex, payloadSize)
            return
        }

        val initialCapacity = if (packet.size() > 0) packet.size() else DEFAULT_INITIAL_CAPACITY
        // Direct buffer required by Zstd ByteBuffer compression.
        val payloadBuf = out.alloc().directBuffer(initialCapacity)

        try {
            packet.write(NettyWriteBuffer(payloadBuf))
            val uncompressedSize = payloadBuf.readableBytes()

            if (uncompressedSize > definition.maxSize || uncompressedSize > QuicProtocol.MAX_PACKET_SIZE) {
                throw ProtocolException("Packet too large before compression: $uncompressedSize")
            }

            val maxCompressedSize = Zstd.compressBound(uncompressedSize.toLong()).toInt()

            out.ensureWritable(maxCompressedSize)

            val compressedSize = PacketCompression.compress(
                payloadBuf,
                out,
                payloadStart,
                maxCompressedSize,
            )

            if (compressedSize > QuicProtocol.MAX_PACKET_SIZE) {
                throw ProtocolException("Compressed packet too large: $compressedSize")
            }

            out.writerIndex(payloadStart + compressedSize)
            out.setIntLE(lengthIndex, compressedSize)
        } finally {
            payloadBuf.release()
        }
    }

    /**
     * Extracts (and Zstd-decompresses when required) an inbound payload slice of [payloadLength] bytes.
     *
     * The returned [ByteBuf] is owned by the caller, except when [payloadLength] is `0`, in which case
     * [Unpooled.EMPTY_BUFFER] is returned.
     */
    fun extractPayload(
        buf: ByteBuf,
        definition: PacketDefinition,
        payloadLength: Int,
    ): ByteBuf {
        if (payloadLength == 0) {
            return Unpooled.EMPTY_BUFFER
        }

        try {
            if (definition.compressed) {
                val payload = PacketCompression.decompress(
                    buf.alloc(),
                    buf,
                    buf.readerIndex(),
                    payloadLength,
                    minOf(definition.maxSize, QuicProtocol.MAX_PACKET_SIZE),
                )
                buf.skipBytes(payloadLength)
                return payload
            }
            return buf.readRetainedSlice(payloadLength)
        } catch (e: Exception) {
            if (buf.readableBytes() >= payloadLength) {
                buf.skipBytes(payloadLength)
            }
            throw e
        }
    }
}
