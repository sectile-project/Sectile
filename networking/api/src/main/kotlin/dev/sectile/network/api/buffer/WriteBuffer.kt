package dev.sectile.network.api.buffer

import java.util.UUID

/**
 * Byte writer following protocol semantics:
 * - Multi-byte integers are little-endian.
 * - UUIDs follow RFC 4122 (network byte order / big-endian).
 * - VarInts are canonical (7-bit little-endian groups, max 5 bytes).
 *
 * Variable offset slots are reserved with [skip] and populated afterward via [writeIntAt].
 */
interface WriteBuffer {

    fun writeBytes(bytes: ByteArray)

    fun writeVarAsciiString(value: String, maxLength: Int)
    fun writeVarByteArray(bytes: ByteArray, maxLength: Int)
    fun writeFixedAscii(value: String, length: Int)
    fun writeVarString(value: String, maxLength: Int)

    fun writeVarBytes(bytes: ByteArray)

    fun writeBoolean(condition: Boolean)
    fun writeByte(value: Byte)
    fun writeByte(value: Int)
    fun writeShort(value: Int)
    fun writeInt(value: Int)
    fun writeIntAt(index: Int, value: Int)
    fun writeVarInt(value: Int)
    fun writeLong(value: Long)
    fun writeFloat(value: Float)

    fun writeDouble(value: Double)

    fun reserveOffset(offset: Int = 4): Int {
        val slot = index
        skip(offset)
        return slot
    }

    fun writeUUID(value: UUID)

    fun revert(amountBytes: Int)
    fun skip(amountBytes: Int)

    val index: Int
}

inline fun <T : Any> WriteBuffer.writeSlot(
    slot: Int,
    varBlockStart: Int,
    value: T?,
    write: (T) -> Unit,
) {
    if (value == null) {
        writeIntAt(slot, -1)
    } else {
        writeIntAt(slot, index - varBlockStart)
        write(value)
    }
}
