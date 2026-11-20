package dev.sectile.network.protocol.player

import dev.sectile.network.api.buffer.WriteBuffer
import dev.sectile.network.api.packet.OutboundPacket
import dev.sectile.network.api.packet.PacketDefinition
import dev.sectile.network.protocol.ProtocolPacketTable

/**
 * Outbound `SetEntitySeed` packet (id 160, server -> client).
 *
 * Sets the seed used by the server to generate world entity IDs.
 */
class SetEntitySeedPacket(
    var entitySeed: Int = 0,
) : OutboundPacket {

    override val definition: PacketDefinition
        get() = ProtocolPacketTable.SET_ENTITY_SEED

    override fun write(buf: WriteBuffer) {
        buf.writeInt(entitySeed)
    }

    override fun size(): Int = ProtocolPacketTable.SET_ENTITY_SEED.maxSize
}
