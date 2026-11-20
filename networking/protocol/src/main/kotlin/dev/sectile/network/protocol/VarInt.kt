package dev.sectile.network.protocol

import dev.sectile.network.api.buffer.ReadBuffer
import dev.sectile.network.api.buffer.WriteBuffer

/**
 * Canonical varint of the Hytale protocol.
 *
 * Format: 7-bit groups in little-endian, the high bit marks "continues". An int32
 * occupies at most 5 bytes.
 *
 * Only canonical encodings are accepted (those written by the protocol sender):
 *  - the terminating byte of an intermediate group cannot be `0` (it would be an alias of a
 *    shorter value),
 *  - the fifth byte can only be between 1 and 7 (bits 28..31 of the int).
 */
object VarInt {

    const val MAX_BYTES: Int = 5

    fun sizeOf(value: Int): Int {
        if (value < 0) throw ProtocolFormatException("VarInt cannot encode negative values: $value")
        if (value == 0) return 1
        return (32 - Integer.numberOfLeadingZeros(value) + 6) / 7
    }

    fun write(buf: WriteBuffer, value: Int) {
        if (value < 0) throw ProtocolFormatException("VarInt cannot encode negative values: $value")
        var remaining = value
        while (remaining and 0x7F.inv() != 0) {
            buf.writeByte((remaining and 0x7F) or 0x80)
            remaining = remaining ushr 7
        }
        buf.writeByte(remaining)
    }

    fun read(buf: ReadBuffer): Int {
        var result = 0
        var shift = 0
        for (index in 0 until MAX_BYTES) {
            val b = buf.readUnsignedByte()
            if (index == MAX_BYTES - 1) {
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
}
