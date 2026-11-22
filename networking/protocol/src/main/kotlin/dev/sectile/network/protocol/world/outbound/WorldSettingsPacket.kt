package dev.sectile.network.protocol.world.outbound

import dev.sectile.network.api.buffer.WriteBuffer
import dev.sectile.network.api.packet.OutboundPacket
import dev.sectile.network.api.packet.PacketDefinition
import dev.sectile.network.protocol.DataSize
import dev.sectile.network.protocol.ProtocolPacketTable
import dev.sectile.network.protocol.asset.structures.Asset

class WorldSettingsPacket(
    var requiredAssets: MutableList<Asset>? = null,
) : OutboundPacket {

    override val definition: PacketDefinition = ProtocolPacketTable.WORLD_SETTINGS

    override fun write(buf: WriteBuffer) {
        val assets = requiredAssets
        buf.writeByte(if (assets == null) 0 else 0x01)
        if (assets != null) {
            buf.writeVarInt(assets.size)
            assets.forEach { it.write(buf) }
        }
    }

    override fun size(): Int = DataSize.BYTE + (requiredAssets?.let { DataSize.varListSize(it, Asset::computeSize) } ?: 0)
}
