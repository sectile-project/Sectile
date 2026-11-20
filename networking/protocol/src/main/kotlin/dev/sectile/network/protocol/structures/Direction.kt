package dev.sectile.network.protocol.structures

import dev.sectile.network.api.buffer.ReadBuffer
import dev.sectile.network.api.buffer.WriteBuffer

/**
 * Orientation (yaw, pitch, roll) in radians: 3 floats in little-endian.
 */
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
        /** Fixed size of the structure. */
        const val SIZE: Int = 12
    }
}
