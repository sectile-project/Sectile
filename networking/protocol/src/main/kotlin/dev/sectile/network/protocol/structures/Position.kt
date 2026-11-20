package dev.sectile.network.protocol.structures

import dev.sectile.network.api.buffer.ReadBuffer
import dev.sectile.network.api.buffer.WriteBuffer

/**
 * Absolute world position (3 doubles in little-endian).
 */
class Position(
    var x: Double = 0.0,
    var y: Double = 0.0,
    var z: Double = 0.0,
) {

    fun write(buf: WriteBuffer) {
        buf.writeDouble(x)
        buf.writeDouble(y)
        buf.writeDouble(z)
    }

    fun read(buf: ReadBuffer) {
        x = buf.readDouble()
        y = buf.readDouble()
        z = buf.readDouble()
    }

    override fun toString(): String = "($x, $y, $z)"

    companion object {
        /** Fixed size of the structure. */
        const val SIZE: Int = 24
    }
}
