package dev.sectile.network.protocol.setup

import dev.sectile.network.api.buffer.WriteBuffer
import dev.sectile.network.api.packet.OutboundPacket
import dev.sectile.network.api.packet.PacketDefinition
import dev.sectile.network.protocol.ProtocolPacketTable

/**
 * Outbound `AssetFinalize` packet (id 26, server -> client).
 *
 * Completes an asset transfer batch. Empty payload.
 */
class AssetFinalizePacket : OutboundPacket {

    override val definition: PacketDefinition
        get() = ProtocolPacketTable.ASSET_FINALIZE

    override fun write(buf: WriteBuffer) = Unit

    override fun size(): Int = 0
}
