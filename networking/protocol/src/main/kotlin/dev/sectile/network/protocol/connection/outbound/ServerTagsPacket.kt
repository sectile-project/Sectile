package dev.sectile.network.protocol.connection.outbound

import dev.sectile.network.api.buffer.WriteBuffer
import dev.sectile.network.api.packet.OutboundPacket
import dev.sectile.network.api.packet.PacketDefinition
import dev.sectile.network.protocol.DataSize
import dev.sectile.network.protocol.ProtocolPacketTable

class ServerTagsPacket(
    var tags: MutableMap<String, Int>? = null,
) : OutboundPacket {

    override val definition: PacketDefinition = ProtocolPacketTable.SERVER_TAGS

    override fun write(buf: WriteBuffer) {
        val current = tags
        buf.writeByte(if (current == null) 0 else 0x01)
        if (current != null) {
            buf.writeVarInt(current.size)
            for ((key, value) in current) {
                buf.writeVarString(key, MAX_TAG_LENGTH)
                buf.writeVarInt(value)
            }
        }
    }

    override fun size(): Int {
        var size = DataSize.BYTE
        tags?.let { map ->
            size += DataSize.varInt(map.size)
            map.forEach { (key, value) ->
                size += DataSize.varStringSize(key) + DataSize.varInt(value)
            }
        }
        return size
    }

    companion object {
        const val MAX_TAG_LENGTH: Int = DataSize.MAX_STRING_LENGTH
    }
}
