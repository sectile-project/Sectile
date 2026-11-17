package dev.sectile.network.api.buffer

import java.util.UUID

/**
 * Byte reader following Hytale protocol semantics:
 * - Multi-byte integers are little-endian.
 * - UUIDs follow RFC 4122 (network byte order / big-endian).
 * - VarInts are canonical (7-bit little-endian groups, max 5 bytes).
 *
 * Out-of-bounds reads throw an exception handled by the decoder/inbound layer
 * as a protocol violation.
 */
interface ReadBuffer {

    /** Reads [length] bytes and advances the cursor. */
    fun readBytes(length: Int): ByteArray

    fun readBoolean(): Boolean

    fun readByte(): Byte

    /** Reads an unsigned byte and advances the cursor. */
    fun readUnsignedByte(): Int

    /** Reads a little-endian 16-bit integer and advances the cursor. */
    fun readShort(): Short

    /** Reads a little-endian 32-bit integer and advances the cursor. */
    fun readInt(): Int

    /** Reads a little-endian 64-bit integer and advances the cursor. */
    fun readLong(): Long

    fun readFloat(): Float

    fun readDouble(): Double

    /** Reads an RFC 4122 (big-endian) UUID and advances the cursor. */
    fun readUUID(): UUID

    /** Reads a canonical VarInt and advances the cursor. */
    fun readVarInt(): Int

    /** Reads a little-endian 32-bit integer at absolute [index] without moving the cursor. */
    fun readIntAt(index: Int): Int

    /** Moves the cursor to absolute [index]. */
    fun skipTo(index: Int)

    /** Advances the cursor by [amount] bytes. */
    fun skip(amount: Int)

    /** Current cursor position. */
    val index: Int
}
