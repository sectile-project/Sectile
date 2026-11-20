package dev.sectile.network.protocol

import dev.sectile.network.api.buffer.ReadBuffer
import dev.sectile.network.api.buffer.WriteBuffer
import java.util.UUID

/**
 * In-memory test buffers matching Hytale protocol wire semantics (little-endian primitives,
 * RFC 4122 big-endian UUIDs, and canonical VarInts) without Netty dependencies.
 */
class TestWriteBuffer(capacity: Int = 4096) : WriteBuffer {

    private val data = ByteArray(capacity)
    private var pos = 0

    override fun writeBytes(bytes: ByteArray) {
        System.arraycopy(bytes, 0, data, pos, bytes.size)
        pos += bytes.size
    }

    override fun writeBoolean(condition: Boolean) {
        writeByte(if (condition) 1 else 0)
    }

    override fun writeByte(value: Byte) {
        data[pos++] = value
    }

    override fun writeByte(value: Int) {
        data[pos++] = value.toByte()
    }

    override fun writeShort(value: Int) {
        data[pos++] = (value and 0xFF).toByte()
        data[pos++] = ((value shr 8) and 0xFF).toByte()
    }

    override fun writeInt(value: Int) {
        for (shift in 0 until 4) {
            data[pos++] = ((value shr (shift * 8)) and 0xFF).toByte()
        }
    }

    override fun writeLong(value: Long) {
        for (shift in 0 until 8) {
            data[pos++] = ((value shr (shift * 8)) and 0xFF).toByte()
        }
    }

    override fun writeFloat(value: Float) {
        writeInt(value.toRawBits())
    }

    override fun writeDouble(value: Double) {
        writeLong(value.toRawBits())
    }

    override fun writeUUID(value: UUID) {
        val msb = value.mostSignificantBits
        val lsb = value.leastSignificantBits
        for (shift in 56 downTo 0 step 8) {
            data[pos++] = ((msb shr shift) and 0xFF).toByte()
        }
        for (shift in 56 downTo 0 step 8) {
            data[pos++] = ((lsb shr shift) and 0xFF).toByte()
        }
    }

    override fun writeVarInt(value: Int) {
        VarInt.write(this, value)
    }

    override fun writeIntAt(index: Int, value: Int) {
        for (shift in 0 until 4) {
            data[index + shift] = ((value shr (shift * 8)) and 0xFF).toByte()
        }
    }

    override fun revert(amountBytes: Int) {
        pos -= amountBytes
    }

    override fun skip(amountBytes: Int) {
        pos += amountBytes
    }

    override val index: Int
        get() = pos

    fun toByteArray(): ByteArray = data.copyOf(pos)
}

class TestReadBuffer(private val data: ByteArray) : ReadBuffer {

    private var pos = 0

    override fun readBytes(length: Int): ByteArray {
        val out = data.copyOfRange(pos, pos + length)
        pos += length
        return out
    }

    override fun readBoolean(): Boolean = readUnsignedByte() != 0

    override fun readByte(): Byte = data[pos++]

    override fun readUnsignedByte(): Int = data[pos++].toInt() and 0xFF

    override fun readShort(): Short {
        val value = readUnsignedByte() or (readUnsignedByte() shl 8)
        return value.toShort()
    }

    override fun readInt(): Int {
        var value = 0
        for (shift in 0 until 4) {
            value = value or (readUnsignedByte() shl (shift * 8))
        }
        return value
    }

    override fun readLong(): Long {
        var value = 0L
        for (shift in 0 until 8) {
            value = value or (readUnsignedByte().toLong() shl (shift * 8))
        }
        return value
    }

    override fun readFloat(): Float = Float.fromBits(readInt())

    override fun readDouble(): Double = Double.fromBits(readLong())

    override fun readUUID(): UUID {
        var msb = 0L
        for (i in 0 until 8) {
            msb = (msb shl 8) or readUnsignedByte().toLong()
        }
        var lsb = 0L
        for (i in 0 until 8) {
            lsb = (lsb shl 8) or readUnsignedByte().toLong()
        }
        return UUID(msb, lsb)
    }

    override fun readVarInt(): Int = VarInt.read(this)

    override fun readIntAt(index: Int): Int {
        var value = 0
        for (shift in 0 until 4) {
            value = value or ((data[index + shift].toInt() and 0xFF) shl (shift * 8))
        }
        return value
    }

    override fun skipTo(index: Int) {
        pos = index
    }

    override fun skip(amount: Int) {
        pos += amount
    }

    override val index: Int
        get() = pos
}
