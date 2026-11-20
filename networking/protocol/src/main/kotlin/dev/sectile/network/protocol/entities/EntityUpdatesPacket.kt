package dev.sectile.network.protocol.entities

import dev.sectile.network.api.buffer.WriteBuffer
import dev.sectile.network.api.packet.OutboundPacket
import dev.sectile.network.api.packet.PacketDefinition
import dev.sectile.network.protocol.ProtocolCodec
import dev.sectile.network.protocol.ProtocolPacketTable
import dev.sectile.network.protocol.VarInt

/**
 * Outbound `EntityUpdates` packet (id 161, server -> client, Zstd compressed).
 *
 * Updates and removals for world entities.
 *
 * Layout: nullability bitmask (1 byte), two 4-byte offset slots, and the variable block
 * containing removed entity IDs and entity update entries.
 */
class EntityUpdatesPacket(
    var removed: MutableList<Int>? = null,
    var updates: MutableList<EntityUpdate>? = null,
) : OutboundPacket {

    override val definition: PacketDefinition
        get() = ProtocolPacketTable.ENTITY_UPDATES

    override fun write(buf: WriteBuffer) {
        var nullBits = 0
        if (removed != null) nullBits = nullBits or 0x01
        if (updates != null) nullBits = nullBits or 0x02

        buf.writeByte(nullBits)

        val removedSlot = ProtocolCodec.reserveOffset(buf)
        val updatesSlot = ProtocolCodec.reserveOffset(buf)

        val varBlockStart = buf.index

        if (removed == null) {
            buf.writeIntAt(removedSlot, -1)
        } else {
            buf.writeIntAt(removedSlot, buf.index - varBlockStart)
            VarInt.write(buf, removed!!.size)
            removed!!.forEach { buf.writeInt(it) }
        }

        if (updates == null) {
            buf.writeIntAt(updatesSlot, -1)
        } else {
            buf.writeIntAt(updatesSlot, buf.index - varBlockStart)
            VarInt.write(buf, updates!!.size)
            updates!!.forEach { it.write(buf) }
        }
    }

    override fun size(): Int {
        var size = VARIABLE_BLOCK_START
        removed?.let { size += VarInt.sizeOf(it.size) + it.size * 4 }
        updates?.let { list ->
            size += VarInt.sizeOf(list.size)
            list.forEach { size += it.computeSize() }
        }
        return size
    }

    companion object {
        /** Start offset of the variable block. */
        const val VARIABLE_BLOCK_START: Int = 9

        /** Maximum entity count per update list. */
        const val MAX_ENTITIES: Int = 4_096_000
    }
}
