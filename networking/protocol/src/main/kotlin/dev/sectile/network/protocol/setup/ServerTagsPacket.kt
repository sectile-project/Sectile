package dev.sectile.network.protocol.setup

import dev.sectile.network.api.buffer.WriteBuffer
import dev.sectile.network.api.packet.OutboundPacket
import dev.sectile.network.api.packet.PacketDefinition
import dev.sectile.network.protocol.ProtocolCodec
import dev.sectile.network.protocol.ProtocolPacketTable
import dev.sectile.network.protocol.VarInt

/**
 * Outbound `ServerTags` packet (id 34, server -> client).
 *
 * Sends server tag mappings (string to int) inline without an offset slot.
 */
class ServerTagsPacket(
    var tags: MutableMap<String, Int>? = null,
) : OutboundPacket {

    override val definition: PacketDefinition
        get() = ProtocolPacketTable.SERVER_TAGS

    override fun write(buf: WriteBuffer) {
        buf.writeByte(if (tags == null) 0 else 0x01)
        if (tags != null) {
            VarInt.write(buf, tags!!.size)
            for ((key, value) in tags!!) {
                ProtocolCodec.writeVarString(buf, key, MAX_TAG_LENGTH)
                VarInt.write(buf, value)
            }
        }
    }

    override fun size(): Int {
        var size = 1
        tags?.let { map ->
            size += VarInt.sizeOf(map.size)
            map.forEach { (key, value) ->
                val bytes = key.toByteArray(Charsets.UTF_8).size
                size += VarInt.sizeOf(bytes) + bytes
                size += VarInt.sizeOf(value)
            }
        }
        return size
    }

    companion object {
        /** Maximum tag key string length. */
        const val MAX_TAG_LENGTH: Int = 4_096_000
    }
}
