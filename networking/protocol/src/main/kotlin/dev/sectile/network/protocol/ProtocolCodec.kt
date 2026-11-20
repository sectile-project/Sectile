package dev.sectile.network.protocol

import dev.sectile.network.api.buffer.ReadBuffer
import dev.sectile.network.api.buffer.WriteBuffer

/**
 * Read/write utilities for the Hytale protocol.
 *
 * Centralizes the rules that the C# generator applies in `PacketReader` / `PacketWriter`:
 * varint-prefixed strings with validated length, ASCII strings, byte arrays, and offset
 * slots with canonical validation.
 */
object ProtocolCodec {

    /** Maximum length allowed for a protocol string. */
    const val MAX_STRING_LENGTH: Int = 4_096_000

    /**
     * Reads a varint-prefixed string (validated UTF-8).
     *
     * @param maxLength maximum length in bytes.
     * @param field field name (for the error).
     */
    fun readVarString(buf: ReadBuffer, maxLength: Int, field: String): String {
        val length = readLength(buf, maxLength, field)
        val bytes = buf.readBytes(length)
        return decodeUtf8(bytes, field)
    }

    /**
     * Reads a varint-prefixed string that can only contain ASCII.
     */
    fun readVarAsciiString(buf: ReadBuffer, maxLength: Int, field: String): String {
        val length = readLength(buf, maxLength, field)
        val bytes = buf.readBytes(length)
        if (!isValidAscii(bytes)) throw ProtocolFormatException("Invalid ASCII string in field '$field'")
        return decodeAscii(bytes)
    }

    /**
     * Reads a varint-prefixed byte array.
     */
    fun readVarByteArray(buf: ReadBuffer, maxLength: Int, field: String): ByteArray {
        val length = readLength(buf, maxLength, field)
        return buf.readBytes(length)
    }

    /**
     * Reads a fixed-length ASCII string, truncated at the first null byte.
     */
    fun readFixedAscii(buf: ReadBuffer, length: Int): String {
        val bytes = buf.readBytes(length)
        val end = bytes.indexOf(0).let { if (it < 0) bytes.size else it }
        return decodeAscii(bytes.copyOf(end))
    }

    /**
     * Writes a varint-prefixed string (UTF-8).
     */
    fun writeVarString(buf: WriteBuffer, value: String, maxLength: Int) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        if (bytes.size > maxLength) {
            throw ProtocolFormatException("String too long: ${bytes.size} > $maxLength")
        }
        VarInt.write(buf, bytes.size)
        buf.writeBytes(bytes)
    }

    /**
     * Writes a varint-prefixed string (ASCII).
     */
    fun writeVarAsciiString(buf: WriteBuffer, value: String, maxLength: Int) {
        val bytes = encodeAscii(value)
        if (bytes.size > maxLength) {
            throw ProtocolFormatException("String too long: ${bytes.size} > $maxLength")
        }
        VarInt.write(buf, bytes.size)
        buf.writeBytes(bytes)
    }

    /**
     * Writes a varint-prefixed byte array.
     */
    fun writeVarByteArray(buf: WriteBuffer, bytes: ByteArray, maxLength: Int) {
        if (bytes.size > maxLength) {
            throw ProtocolFormatException("Byte array too long: ${bytes.size} > $maxLength")
        }
        VarInt.write(buf, bytes.size)
        buf.writeBytes(bytes)
    }

    /**
     * Writes a fixed-length ASCII string, padding with zeros.
     */
    fun writeFixedAscii(buf: WriteBuffer, value: String, length: Int) {
        val bytes = encodeAscii(value)
        if (bytes.size > length) {
            throw ProtocolFormatException("String too long: ${bytes.size} > $length")
        }
        buf.writeBytes(bytes)
        if (bytes.size < length) buf.skip(length - bytes.size)
    }

    /**
     * Reserves a 4-byte offset slot and returns its position.
     */
    fun reserveOffset(buf: WriteBuffer): Int {
        val slot = buf.index
        buf.skip(4)
        return slot
    }

    /**
     * Checks that the offset slot at [slotPosition] equals [expected].
     *
     * A present field must start exactly where the previous field ended, and an absent one
     * must hold -1: this way random access and sequential reading describe the same
     * bytes.
     */
    fun requireSlot(buf: ReadBuffer, slotPosition: Int, expected: Int, field: String) {
        val slot = buf.readIntAt(slotPosition)
        if (slot != expected) {
            throw ProtocolFormatException(
                "Non-canonical layout for field '$field': slot $slot != expected $expected",
            )
        }
    }

    private fun readLength(buf: ReadBuffer, maxLength: Int, field: String): Int {
        val length = VarInt.read(buf)
        if (length < 0) throw ProtocolFormatException("Negative length in field '$field': $length")
        if (length > maxLength) {
            throw ProtocolFormatException("String too long in field '$field': $length > $maxLength")
        }
        return length
    }

    /** `true` if all bytes are ASCII (0..127). */
    fun isValidAscii(bytes: ByteArray): Boolean {
        for (b in bytes) {
            if (b < 0) return false
        }
        return true
    }

    /**
     * `true` if the bytes are well-formed UTF-8 (no overlong encodings, no
     * invalid surrogates, and no encoding of a character outside Unicode).
     */
    fun isWellFormedUtf8(bytes: ByteArray): Boolean {
        var i = 0
        while (i < bytes.size) {
            val b0 = bytes[i].toInt() and 0xFF
            if (b0 < 0x80) {
                i++
                continue
            }
            if (b0 < 0xC2 || b0 > 0xF4) return false

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
            if (b1 < lower || b1 > upper) return false
            for (k in 2..trail) {
                val bk = bytes[i + k].toInt() and 0xFF
                if (bk < 0x80 || bk > 0xBF) return false
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

    /** Decodes ASCII; out-of-range bytes become '?', as in .NET. */
    fun decodeAscii(bytes: ByteArray): String {
        val chars = CharArray(bytes.size)
        for (i in bytes.indices) {
            val b = bytes[i].toInt() and 0xFF
            chars[i] = if (b <= 0x7F) b.toChar() else '?'
        }
        return String(chars)
    }

    /** Encodes to ASCII; out-of-range characters become '?'. */
    fun encodeAscii(value: String): ByteArray {
        val bytes = ByteArray(value.length)
        for (i in value.indices) {
            val c = value[i].code
            bytes[i] = if (c <= 0x7F) c.toByte() else '?'.code.toByte()
        }
        return bytes
    }
}
