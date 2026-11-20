package dev.sectile.network.protocol.structures

import dev.sectile.network.api.buffer.ReadBuffer
import dev.sectile.network.api.buffer.WriteBuffer

/**
 * Transformation of an entity (`ModelTransform` of the Hytale protocol).
 *
 * Model position and orientation: 1 byte of nulls, the position (24 bytes), and the two
 * orientations (12 bytes each).
 */
class ModelTransform(
    var position: Position? = null,
    var bodyOrientation: Direction? = null,
    var lookOrientation: Direction? = null,
) {

    fun write(buf: WriteBuffer) {
        var nullBits = 0
        if (position != null) nullBits = nullBits or 0x01
        if (bodyOrientation != null) nullBits = nullBits or 0x02
        if (lookOrientation != null) nullBits = nullBits or 0x04

        buf.writeByte(nullBits)

        position?.write(buf) ?: buf.skip(Position.SIZE)
        bodyOrientation?.write(buf) ?: buf.skip(Direction.SIZE)
        lookOrientation?.write(buf) ?: buf.skip(Direction.SIZE)
    }

    fun read(buf: ReadBuffer) {
        val nullBits = buf.readUnsignedByte()
        position = if (nullBits and 0x01 != 0) Position().also { it.read(buf) } else null
        bodyOrientation = if (nullBits and 0x02 != 0) Direction().also { it.read(buf) } else null
        lookOrientation = if (nullBits and 0x04 != 0) Direction().also { it.read(buf) } else null
    }

    companion object {
        /** Fixed size of the structure. */
        const val SIZE: Int = 49
    }
}
