package dev.sectile.network.api.buffer

import java.util.UUID

/**
 * Byte reader following protocol semantics:
 * - Multi-byte integers are little-endian.
 * - UUIDs follow RFC 4122 (network byte order / big-endian).
 * - VarInts are canonical (7-bit little-endian groups, max 5 bytes).
 *
 * Out-of-bounds reads throw an exception handled by the decoder/inbound layer
 * as a protocol violation.
 */
interface ReadBuffer {

    fun readBytes(length: Int): ByteArray

    fun readBoolean(): Boolean

    fun readVarString(maxLength: Int, field: String): String
    fun readVarAsciiString(maxLength: Int, field: String): String
    fun readVarByteArray(maxLength: Int, field: String): ByteArray
    fun readFixedAscii(length: Int): String

    /**
     * Checks that the offset slot at [slotPosition] equals [expected].
     *
     * A present field must start exactly where the previous field ended, and an absent one
     * must hold -1: this way random access and sequential reading describe the same
     * bytes.
     */
    fun requireSlot(slotPosition: Int, expected: Int, field: String)

    fun readByte(): Byte
    fun readUnsignedByte(): Int
    fun readShort(): Short
    fun readInt(): Int
    fun readLong(): Long
    fun readFloat(): Float
    fun readDouble(): Double
    fun readUUID(): UUID
    fun readVarInt(): Int

    fun readIntAt(index: Int): Int

    fun skipTo(index: Int)

    /** Advances the cursor by [amount] bytes. */
    fun skip(amount: Int)

    /** Current cursor position. */
    val index: Int
}

inline fun <T : Any> ReadBuffer.readSlot(
    slotPosition: Int,
    varBlockStart: Int,
    present: Boolean,
    field: String,
    read: () -> T,
): T? {
    if (!present) {
        requireSlot(slotPosition, -1, field)
        return null
    }
    requireSlot(slotPosition, index - varBlockStart, field)
    return read()
}
