package dev.sectile.network.protocol.interfaceui

import dev.sectile.network.api.buffer.WriteBuffer
import dev.sectile.network.api.packet.OutboundPacket
import dev.sectile.network.api.packet.PacketDefinition
import dev.sectile.network.protocol.ProtocolCodec
import dev.sectile.network.protocol.ProtocolFormatException
import dev.sectile.network.protocol.ProtocolPacketTable
import dev.sectile.network.protocol.VarInt

class ArgCacheInvalidationPacket(
    var argTypeIds: List<String>? = null,
) : OutboundPacket {

    override val definition: PacketDefinition
        get() = ProtocolPacketTable.ARG_CACHE_INVALIDATION

    override fun write(buf: WriteBuffer) {
        val current = argTypeIds
        if (current == null) {
            buf.writeByte(0)
            return
        }
        if (current.size > ProtocolCodec.MAX_STRING_LENGTH) {
            throw ProtocolFormatException("Array too long in field 'ArgTypeIds': ${current.size}")
        }
        buf.writeByte(0x01)
        VarInt.write(buf, current.size)
        current.forEach { ProtocolCodec.writeVarString(buf, it, ProtocolCodec.MAX_STRING_LENGTH) }
    }

    override fun size(): Int {
        var total = VARIABLE_BLOCK_START
        argTypeIds?.let { list ->
            total += VarInt.sizeOf(list.size)
            list.forEach {
                val bytes = it.toByteArray(Charsets.UTF_8).size
                total += VarInt.sizeOf(bytes) + bytes
            }
        }
        return total
    }

    companion object {
        const val VARIABLE_BLOCK_START: Int = 1
    }
}
