package dev.sectile.network.internal.packet.buffer

import dev.sectile.network.api.buffer.ReadBuffer
import dev.sectile.network.protocol.ProtocolFormatException
import io.netty.buffer.ByteBuf
import java.util.UUID

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

    override fun requireSlot(slotPosition: Int, expected: Int, field: String) {
        val slot = readIntAt(slotPosition)
        if (slot != expected) {
            throw ProtocolFormatException(
                "Non-canonical layout for field '$field': slot $slot != expected $expected",
            )
        }
    }

    private fun readLength(maxLength: Int, field: String): Int {
        val length = readVarInt()
        if (length < 0) throw ProtocolFormatException("Negative length in field '$field': $length")
        if (length > maxLength) {
            throw ProtocolFormatException("String too long in field '$field': $length > $maxLength")
        }
        return length
    }

    override fun readVarString(maxLength: Int, field: String): String {
        val length = readLength(maxLength, field)
        val bytes = readBytes(length)
        return decodeUtf8(bytes, field)
    }

    override fun readVarAsciiString(maxLength: Int, field: String): String {
        val length = readLength(maxLength, field)
        val bytes = readBytes(length)
        if (!isValidAscii(bytes)) throw ProtocolFormatException("Invalid ASCII string in field '$field'")
        return decodeAscii(bytes)
    }

    override fun readVarByteArray(maxLength: Int, field: String): ByteArray {
        val length = readLength(maxLength, field)
        return readBytes(length)
    }

    override fun readFixedAscii(length: Int): String {
        val bytes = readBytes(length)
        val end = bytes.indexOf(0).let { if (it < 0) bytes.size else it }
        return decodeAscii(bytes.copyOf(end))
    }

    /** Decodes ASCII; out-of-range bytes become '?', as in .NET. */
    fun decodeAscii(bytes: ByteArray): String {
        val chars = CharArray(bytes.size)
        for (i in bytes.indices) {
            val b = bytes[i].toInt() and 0xFF
            chars[i] = if (b <= 0x7F) b.toChar() else '?'
        }
        return String(chars)
    }

    fun isValidAscii(bytes: ByteArray): Boolean {
        for (b in bytes) {
            if (b < 0) return false
        }
        return true
    }

    fun isWellFormedUtf8(bytes: ByteArray): Boolean {
        var i = 0
        while (i < bytes.size) {
            val b0 = bytes[i].toInt() and 0xFF
            if (b0 < 0x80) {
                i++
                continue
            }
            if (b0 !in 0xC2..0xF4) return false

            val lower: Int
            val upper: Int
            val trail: Int
            if (b0 < 0xE0) {
                lower = 0x80
                upper = 0xBF
                trail = 1
            } else if (b0 < 0xF0) {
                lower = if (b0 == 0xE0) 0xA0 else 0x80
                upper = if (b0 == 0xED) 0x9F else 0xBF
                trail = 2
            } else {
                lower = if (b0 == 0xF0) 0x90 else 0x80
                upper = if (b0 == 0xF4) 0x8F else 0xBF
                trail = 3
            }

            if (i + trail >= bytes.size) return false
            val b1 = bytes[i + 1].toInt() and 0xFF
            if (b1 !in lower..upper) return false
            for (k in 2..trail) {
                val bk = bytes[i + k].toInt() and 0xFF
                if (bk !in 0x80..0xBF) return false
            }
            i += trail + 1
        }
        return true
    }

    /** Decodes UTF-8, first validating that the bytes are well-formed. */
    fun decodeUtf8(bytes: ByteArray, field: String): String {
        if (!isWellFormedUtf8(bytes)) {
            throw ProtocolFormatException("Malformed UTF-8 string in field '$field'")
        }
        return String(bytes, Charsets.UTF_8)
    }

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

    override fun readVarInt(): Int {
        var result = 0
        var shift = 0
        for (index in 0 until 5) {
            val b = readUnsignedByte()
            if (index == 4) {
                // Only bits 28..31 of the int are valid: the byte must be between 1 and 7.
                if (b > 7) throw ProtocolFormatException("Invalid varint: trailing byte out of range ($b)")
                return result or (b shl 28)
            }
            result = result or ((b and 0x7F) shl shift)
            if (b and 0x80 == 0) {
                // A 0 terminator in an intermediate group would encode a shorter value.
                if (index > 0 && b == 0) throw ProtocolFormatException("Invalid varint: non-canonical encoding")
                return result
            }
            shift += 7
        }
        throw ProtocolFormatException("Invalid varint: too long")
    }

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
