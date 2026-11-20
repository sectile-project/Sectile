package dev.sectile.network.protocol.structures

import dev.sectile.network.api.buffer.ReadBuffer
import dev.sectile.network.api.buffer.WriteBuffer

/**
 * Relative half-precision floating-point position (3 shorts in little-endian).
 */
class HalfFloatPosition(
    var x: Short = 0,
    var y: Short = 0,
    var z: Short = 0,
) {

    fun write(buf: WriteBuffer) {
        buf.writeShort(x.toInt())
        buf.writeShort(y.toInt())
        buf.writeShort(z.toInt())
    }

    fun read(buf: ReadBuffer) {
        x = buf.readShort()
        y = buf.readShort()
        z = buf.readShort()
    }

    companion object {
        /** Fixed size of the structure. */
        const val SIZE: Int = 6
    }
}
