package dev.sectile.network.protocol.entity.outbound

import dev.sectile.network.api.buffer.WriteBuffer
import dev.sectile.network.api.buffer.writeSlot
import dev.sectile.network.api.packet.OutboundPacket
import dev.sectile.network.api.packet.PacketDefinition
import dev.sectile.network.protocol.DataSize
import dev.sectile.network.protocol.ProtocolPacketTable
import dev.sectile.network.protocol.entity.structures.EntityUpdate

class EntityUpdatesPacket(
    var removed: MutableList<Int>? = null,
    var updates: MutableList<EntityUpdate>? = null,
) : OutboundPacket {

    override val definition: PacketDefinition = ProtocolPacketTable.ENTITY_UPDATES

    override fun write(buf: WriteBuffer) {
        var nullBits = 0
        if (removed != null) nullBits = nullBits or 0x01
        if (updates != null) nullBits = nullBits or 0x02

        buf.writeByte(nullBits)

        val removedSlot = buf.reserveOffset()
        val updatesSlot = buf.reserveOffset()

        val varBlockStart = buf.index

        buf.writeSlot(removedSlot, varBlockStart, removed) { list ->
            buf.writeVarInt(list.size)
            list.forEach { buf.writeInt(it) }
        }
        buf.writeSlot(updatesSlot, varBlockStart, updates) { list ->
            buf.writeVarInt(list.size)
            list.forEach { it.write(buf) }
        }
    }

    override fun size(): Int {
        var size = VARIABLE_BLOCK_START
        removed?.let { size += DataSize.varListSize(it.size, DataSize.INT) }
        updates?.let { size += DataSize.varListSize(it, EntityUpdate::computeSize) }
        return size
    }

    companion object {
        const val VARIABLE_BLOCK_START: Int = 9
        const val MAX_ENTITIES: Int = DataSize.MAX_ARRAY_LENGTH
    }
}
