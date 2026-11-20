package dev.sectile.network.protocol.setup

import dev.sectile.network.api.buffer.WriteBuffer
import dev.sectile.network.api.packet.OutboundPacket
import dev.sectile.network.api.packet.PacketDefinition
import dev.sectile.network.protocol.ProtocolPacketTable
import dev.sectile.network.protocol.VarInt
import dev.sectile.network.protocol.structures.Asset

/**
 * Outbound `WorldSettings` packet (id 20, server -> client, Zstd compressed).
 *
 * Sends world configuration and the list of required assets expected by the client.
 */
class WorldSettingsPacket(
    var requiredAssets: MutableList<Asset>? = null,
) : OutboundPacket {

    override val definition: PacketDefinition
        get() = ProtocolPacketTable.WORLD_SETTINGS

    override fun write(buf: WriteBuffer) {
        buf.writeByte(if (requiredAssets == null) 0 else 0x01)
        if (requiredAssets != null) {
            VarInt.write(buf, requiredAssets!!.size)
            requiredAssets!!.forEach { it.write(buf) }
        }
    }

    override fun size(): Int {
        var size = 1
        requiredAssets?.let { list ->
            size += VarInt.sizeOf(list.size)
            list.forEach {
                val bytes = it.name.toByteArray(Charsets.UTF_8).size
                size += Asset.HASH_LENGTH + VarInt.sizeOf(bytes) + bytes
            }
        }
        return size
    }
}
