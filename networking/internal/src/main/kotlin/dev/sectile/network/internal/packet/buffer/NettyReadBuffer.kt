package dev.sectile.network.internal.packet.buffer

import dev.sectile.network.api.buffer.ReadBuffer
import dev.sectile.network.protocol.VarInt
import io.netty.buffer.ByteBuf
import java.util.UUID

/**
 * Reader backed by a Netty [ByteBuf] following Hytale protocol semantics:
 * little-endian multi-byte integers and RFC 4122 (big-endian) UUIDs.
 */
class NettyReadBuffer(
    private val buffer: ByteBuf,
) : ReadBuffer {

    override fun readBytes(length: Int): ByteArray = ByteArray(length).also { buffer.readBytes(it) }

    override fun readBoolean(): Boolean = buffer.readBoolean()

    override fun readByte(): Byte = buffer.readByte()

    override fun readUnsignedByte(): Int = buffer.readUnsignedByte().toInt()

    override fun readShort(): Short = buffer.readShortLE()

    override fun readInt(): Int = buffer.readIntLE()

    override fun readLong(): Long = buffer.readLongLE()

    override fun readFloat(): Float = buffer.readFloatLE()

    override fun readDouble(): Double = buffer.readDoubleLE()

    override fun readUUID(): UUID {
        var mostSigBits = 0L
        for (i in 0 until 8) {
            mostSigBits = (mostSigBits shl 8) or (readUnsignedByte().toLong() and 0xFF)
        }
        var leastSigBits = 0L
        for (i in 0 until 8) {
            leastSigBits = (leastSigBits shl 8) or (readUnsignedByte().toLong() and 0xFF)
        }
        return UUID(mostSigBits, leastSigBits)
    }

    override fun readVarInt(): Int = VarInt.read(this)

    override fun readIntAt(index: Int): Int = buffer.getIntLE(index)

    override fun skipTo(index: Int) {
        buffer.readerIndex(index)
    }

    override fun skip(amount: Int) {
        buffer.skipBytes(amount)
    }

    override val index: Int
        get() = buffer.readerIndex()
}
