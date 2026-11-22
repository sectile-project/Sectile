package dev.sectile.network.protocol.shared.math

import dev.sectile.network.api.buffer.ReadBuffer
import dev.sectile.network.api.buffer.WriteBuffer
import dev.sectile.network.protocol.DataSize

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
        const val SIZE: Int = DataSize.SHORT * 3
    }
}
