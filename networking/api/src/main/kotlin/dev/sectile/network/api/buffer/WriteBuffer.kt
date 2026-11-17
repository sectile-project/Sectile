package dev.sectile.network.api.buffer

import java.util.UUID

/**
 * Byte writer following Hytale protocol semantics:
 * - Multi-byte integers are little-endian.
 * - UUIDs follow RFC 4122 (network byte order / big-endian).
 * - VarInts are canonical (7-bit little-endian groups, max 5 bytes).
 *
 * Variable offset slots are reserved with [skip] and populated afterward via [writeIntAt].
 */
interface WriteBuffer {

    /** Writes raw bytes. */
    fun writeBytes(bytes: ByteArray)

    fun writeBoolean(condition: Boolean)

    fun writeByte(value: Byte)

    fun writeByte(value: Int)

    /** Writes a little-endian 16-bit integer. */
    fun writeShort(value: Int)

    /** Writes a little-endian 32-bit integer. */
    fun writeInt(value: Int)

    /** Writes a little-endian 64-bit integer. */
    fun writeLong(value: Long)

    fun writeFloat(value: Float)

    fun writeDouble(value: Double)

    /** Writes an RFC 4122 (big-endian) UUID. */
    fun writeUUID(value: UUID)

    /** Writes a canonical VarInt. */
    fun writeVarInt(value: Int)

    /** Writes a little-endian 32-bit integer at absolute [index] without moving the cursor. */
    fun writeIntAt(index: Int, value: Int)

    /** Moves the cursor backward by [amountBytes]. */
    fun revert(amountBytes: Int)

    /** Advances the cursor forward by [amountBytes]. */
    fun skip(amountBytes: Int)

    /** Current cursor position. */
    val index: Int
}
