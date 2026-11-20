package dev.sectile.network.protocol.structures

import dev.sectile.network.api.buffer.ReadBuffer
import dev.sectile.network.api.buffer.WriteBuffer

/**
 * Teleport confirmation: a one-byte id.
 */
class TeleportAck(
    var teleportId: Int = 0,
) {

    fun write(buf: WriteBuffer) {
        buf.writeByte(teleportId)
    }

    fun read(buf: ReadBuffer) {
        teleportId = buf.readUnsignedByte()
    }

    companion object {
        /** Fixed size of the structure. */
        const val SIZE: Int = 1
    }
}
