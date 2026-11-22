package dev.sectile.network.protocol.shared.math

import dev.sectile.network.api.buffer.ReadBuffer
import dev.sectile.network.api.buffer.WriteBuffer
import dev.sectile.network.protocol.DataSize

class Vector3d(
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
        const val SIZE: Int = DataSize.DOUBLE * 3
    }
}
