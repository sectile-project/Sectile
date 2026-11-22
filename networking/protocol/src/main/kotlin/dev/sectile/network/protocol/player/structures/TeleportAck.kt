package dev.sectile.network.protocol.player.structures

import dev.sectile.network.api.buffer.ReadBuffer
import dev.sectile.network.api.buffer.WriteBuffer
import dev.sectile.network.protocol.DataSize

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
        const val SIZE: Int = DataSize.BYTE
    }
}
