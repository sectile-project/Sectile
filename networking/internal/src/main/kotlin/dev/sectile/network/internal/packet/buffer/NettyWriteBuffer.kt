package dev.sectile.network.internal.packet.buffer

import dev.sectile.network.api.buffer.WriteBuffer
import dev.sectile.network.protocol.VarInt
import io.netty.buffer.ByteBuf
import java.util.UUID

/**
 * Writer backed by a Netty [ByteBuf] following Hytale protocol semantics:
 * little-endian multi-byte integers and RFC 4122 (big-endian) UUIDs.
 */
class NettyWriteBuffer(
    private val buf: ByteBuf,
) : WriteBuffer {

    override fun writeBytes(bytes: ByteArray) {
        buf.writeBytes(bytes)
    }

    override fun writeBoolean(condition: Boolean) {
        buf.writeByte(if (condition) 1 else 0)
    }

    override fun writeByte(value: Byte) {
        buf.writeByte(value.toInt())
    }

    override fun writeByte(value: Int) {
        buf.writeByte(value)
    }

    override fun writeShort(value: Int) {
        buf.writeShortLE(value)
    }

    override fun writeInt(value: Int) {
        buf.writeIntLE(value)
    }

    override fun writeLong(value: Long) {
        buf.writeLongLE(value)
    }

    override fun writeFloat(value: Float) {
        buf.writeFloatLE(value)
    }

    override fun writeDouble(value: Double) {
        buf.writeDoubleLE(value)
    }

    override fun writeUUID(value: UUID) {
        // RFC 4122: the entire UUID is in big-endian (network byte order).
        val mostSigBits = value.mostSignificantBits
        buf.writeByte(((mostSigBits ushr 56) and 0xFF).toInt())
        buf.writeByte(((mostSigBits ushr 48) and 0xFF).toInt())
        buf.writeByte(((mostSigBits ushr 40) and 0xFF).toInt())
        buf.writeByte(((mostSigBits ushr 32) and 0xFF).toInt())
        buf.writeByte(((mostSigBits ushr 24) and 0xFF).toInt())
        buf.writeByte(((mostSigBits ushr 16) and 0xFF).toInt())
        buf.writeByte(((mostSigBits ushr 8) and 0xFF).toInt())
        buf.writeByte((mostSigBits and 0xFF).toInt())

        val leastSigBits = value.leastSignificantBits
        buf.writeByte(((leastSigBits ushr 56) and 0xFF).toInt())
        buf.writeByte(((leastSigBits ushr 48) and 0xFF).toInt())
        buf.writeByte(((leastSigBits ushr 40) and 0xFF).toInt())
        buf.writeByte(((leastSigBits ushr 32) and 0xFF).toInt())
        buf.writeByte(((leastSigBits ushr 24) and 0xFF).toInt())
        buf.writeByte(((leastSigBits ushr 16) and 0xFF).toInt())
        buf.writeByte(((leastSigBits ushr 8) and 0xFF).toInt())
        buf.writeByte((leastSigBits and 0xFF).toInt())
    }

    override fun writeVarInt(value: Int) {
        VarInt.write(this, value)
    }

    override fun writeIntAt(index: Int, value: Int) {
        buf.setIntLE(index, value)
    }

    override fun revert(amountBytes: Int) {
        buf.writerIndex(buf.writerIndex() - amountBytes)
    }

    override fun skip(amountBytes: Int) {
        buf.writeZero(amountBytes)
    }

    override val index: Int
        get() = buf.writerIndex()
}
