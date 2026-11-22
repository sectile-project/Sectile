package dev.sectile.network.protocol.player.outbound

import dev.sectile.network.api.buffer.WriteBuffer
import dev.sectile.network.api.packet.OutboundPacket
import dev.sectile.network.api.packet.PacketDefinition
import dev.sectile.network.protocol.ProtocolPacketTable

class SetEntitySeedPacket(
    var entitySeed: Int = 0,
) : OutboundPacket {

    override val definition: PacketDefinition = ProtocolPacketTable.SET_ENTITY_SEED

    override fun write(buf: WriteBuffer) {
        buf.writeInt(entitySeed)
    }

    override fun size(): Int = ProtocolPacketTable.SET_ENTITY_SEED.maxSize
}
