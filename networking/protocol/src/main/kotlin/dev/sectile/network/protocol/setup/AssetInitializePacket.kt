package dev.sectile.network.protocol.setup

import dev.sectile.network.api.buffer.WriteBuffer
import dev.sectile.network.api.packet.OutboundPacket
import dev.sectile.network.api.packet.PacketDefinition
import dev.sectile.network.protocol.ProtocolPacketTable
import dev.sectile.network.protocol.structures.Asset

/**
 * Outbound `AssetInitialize` packet (id 24, server -> client).
 *
 * Begins transfer of an asset, announcing its descriptor and total byte size before one or more
 * `AssetPart` packets and a closing `AssetFinalize` packet.
 */
class AssetInitializePacket(
    var asset: Asset = Asset(),
    var size: Int = 0,
) : OutboundPacket {

    override val definition: PacketDefinition
        get() = ProtocolPacketTable.ASSET_INITIALIZE

    override fun write(buf: WriteBuffer) {
        buf.writeInt(size)
        asset.write(buf)
    }

    override fun size(): Int = 4 + Asset.MAX_SIZE
}
