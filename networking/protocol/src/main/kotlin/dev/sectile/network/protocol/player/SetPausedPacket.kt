package dev.sectile.network.protocol.player

import dev.sectile.network.api.buffer.ReadBuffer
import dev.sectile.network.api.packet.InboundPacket
import dev.sectile.network.api.packet.PacketDefinition
import dev.sectile.network.protocol.ProtocolPacketTable

/**
 * Inbound `SetPaused` packet (id 158, client -> server).
 *
 * Notifies the server whether the client has paused gameplay.
 */
class SetPausedPacket(
    var paused: Boolean = false,
) : InboundPacket {

    override val definition: PacketDefinition
        get() = ProtocolPacketTable.SET_PAUSED

    override fun read(buf: ReadBuffer) {
        paused = buf.readBoolean()
    }
}
