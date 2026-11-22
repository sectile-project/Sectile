package dev.sectile.network.protocol.world.outbound

import dev.sectile.network.api.buffer.WriteBuffer
import dev.sectile.network.api.packet.OutboundPacket
import dev.sectile.network.api.packet.PacketDefinition
import dev.sectile.network.protocol.ProtocolPacketTable

class ClearChunksPacket : OutboundPacket {

    override val definition: PacketDefinition = ProtocolPacketTable.CLEAR_CHUNKS

    override fun write(buf: WriteBuffer) = Unit

    override fun size(): Int = 0
}
