package dev.sectile.network.protocol.connection.outbound

import dev.sectile.network.api.buffer.WriteBuffer
import dev.sectile.network.api.packet.OutboundPacket
import dev.sectile.network.api.packet.PacketDefinition
import dev.sectile.network.protocol.ProtocolPacketTable

class RequestInsecurePlayerOptionsPacket : OutboundPacket {

    override val definition: PacketDefinition = ProtocolPacketTable.REQUEST_INSECURE_PLAYER_OPTIONS

    override fun write(buf: WriteBuffer) = Unit

    override fun size(): Int = 0
}
