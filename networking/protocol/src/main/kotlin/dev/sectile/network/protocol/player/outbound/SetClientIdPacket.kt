package dev.sectile.network.protocol.player.outbound

import dev.sectile.network.api.buffer.WriteBuffer
import dev.sectile.network.api.packet.OutboundPacket
import dev.sectile.network.api.packet.PacketDefinition
import dev.sectile.network.protocol.ProtocolPacketTable

class SetClientIdPacket(
    var clientId: Int = 0,
) : OutboundPacket {

    override val definition: PacketDefinition = ProtocolPacketTable.SET_CLIENT_ID

    override fun write(buf: WriteBuffer) {
        buf.writeInt(clientId)
    }

    override fun size(): Int = ProtocolPacketTable.SET_CLIENT_ID.maxSize
}
