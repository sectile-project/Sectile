package dev.sectile.network.protocol.world

import dev.sectile.network.api.buffer.WriteBuffer
import dev.sectile.network.api.packet.OutboundPacket
import dev.sectile.network.api.packet.PacketDefinition
import dev.sectile.network.protocol.ProtocolPacketTable

/**
 * Outbound `ClearChunks` packet (id 139, server -> client, chunks channel).
 *
 * Instructs the client to unload all currently loaded chunks. Empty payload.
 */
class ClearChunksPacket : OutboundPacket {

    override val definition: PacketDefinition
        get() = ProtocolPacketTable.CLEAR_CHUNKS

    override fun write(buf: WriteBuffer) = Unit

    override fun size(): Int = 0
}
