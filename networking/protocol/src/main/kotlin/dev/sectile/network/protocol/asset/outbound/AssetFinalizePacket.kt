package dev.sectile.network.protocol.asset.outbound

import dev.sectile.network.api.buffer.WriteBuffer
import dev.sectile.network.api.packet.OutboundPacket
import dev.sectile.network.api.packet.PacketDefinition
import dev.sectile.network.protocol.ProtocolPacketTable

class AssetFinalizePacket : OutboundPacket {

    override val definition: PacketDefinition = ProtocolPacketTable.ASSET_FINALIZE

    override fun write(buf: WriteBuffer) = Unit

    override fun size(): Int = 0
}
