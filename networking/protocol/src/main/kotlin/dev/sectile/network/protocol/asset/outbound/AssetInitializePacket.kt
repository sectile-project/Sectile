package dev.sectile.network.protocol.asset.outbound

import dev.sectile.network.api.buffer.WriteBuffer
import dev.sectile.network.api.packet.OutboundPacket
import dev.sectile.network.api.packet.PacketDefinition
import dev.sectile.network.protocol.DataSize
import dev.sectile.network.protocol.ProtocolPacketTable
import dev.sectile.network.protocol.asset.structures.Asset

class AssetInitializePacket(
    var asset: Asset = Asset(),
    var size: Int = 0,
) : OutboundPacket {

    override val definition: PacketDefinition = ProtocolPacketTable.ASSET_INITIALIZE

    override fun write(buf: WriteBuffer) {
        buf.writeInt(size)
        asset.write(buf)
    }

    override fun size(): Int = DataSize.INT + asset.computeSize()
}
