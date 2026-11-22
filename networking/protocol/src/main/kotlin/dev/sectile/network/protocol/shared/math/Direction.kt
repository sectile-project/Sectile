package dev.sectile.network.protocol.shared.math

import dev.sectile.network.api.buffer.ReadBuffer
import dev.sectile.network.api.buffer.WriteBuffer
import dev.sectile.network.protocol.DataSize

class Direction(
    var yaw: Float = 0f,
    var pitch: Float = 0f,
    var roll: Float = 0f,
) {

    fun write(buf: WriteBuffer) {
        buf.writeFloat(yaw)
        buf.writeFloat(pitch)
        buf.writeFloat(roll)
    }

    fun read(buf: ReadBuffer) {
        yaw = buf.readFloat()
        pitch = buf.readFloat()
        roll = buf.readFloat()
    }

    companion object {
        const val SIZE: Int = DataSize.FLOAT * 3
    }
}
