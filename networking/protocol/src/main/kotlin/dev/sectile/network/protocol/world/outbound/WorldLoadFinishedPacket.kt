package dev.sectile.network.protocol.world.outbound

import dev.sectile.network.api.buffer.WriteBuffer
import dev.sectile.network.api.packet.OutboundPacket
import dev.sectile.network.api.packet.PacketDefinition
import dev.sectile.network.protocol.ProtocolPacketTable

class WorldLoadFinishedPacket : OutboundPacket {

    override val definition: PacketDefinition = ProtocolPacketTable.WORLD_LOAD_FINISHED

    override fun write(buf: WriteBuffer) = Unit

    override fun size(): Int = 0
}
