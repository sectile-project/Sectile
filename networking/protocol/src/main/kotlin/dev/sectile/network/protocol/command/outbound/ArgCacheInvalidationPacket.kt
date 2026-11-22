package dev.sectile.network.protocol.command.outbound

import dev.sectile.network.api.buffer.WriteBuffer
import dev.sectile.network.api.packet.OutboundPacket
import dev.sectile.network.api.packet.PacketDefinition
import dev.sectile.network.protocol.DataSize
import dev.sectile.network.protocol.ProtocolPacketTable
import dev.sectile.network.protocol.command.structures.checkCommandArrayBounds

class ArgCacheInvalidationPacket(
    var argTypeIds: List<String>? = null,
) : OutboundPacket {

    override val definition: PacketDefinition = ProtocolPacketTable.ARG_CACHE_INVALIDATION

    override fun write(buf: WriteBuffer) {
        val current = argTypeIds
        if (current == null) {
            buf.writeByte(0)
            return
        }
        checkCommandArrayBounds(current.size, "ArgTypeIds")
        buf.writeByte(0x01)
        buf.writeVarInt(current.size)
        current.forEach { buf.writeVarString(it, DataSize.MAX_STRING_LENGTH) }
    }

    override fun size(): Int = VARIABLE_BLOCK_START + (argTypeIds?.let(DataSize::stringListSize) ?: 0)

    companion object {
        const val VARIABLE_BLOCK_START: Int = 1
    }
}
