package dev.sectile.network.protocol.player.inbound

import dev.sectile.network.api.buffer.ReadBuffer
import dev.sectile.network.api.packet.InboundPacket
import dev.sectile.network.api.packet.PacketDefinition
import dev.sectile.network.protocol.ProtocolPacketTable

class SetPausedPacket(
    var paused: Boolean = false,
) : InboundPacket {

    override val definition: PacketDefinition = ProtocolPacketTable.SET_PAUSED

    override fun read(buf: ReadBuffer) {
        paused = buf.readBoolean()
    }
}
