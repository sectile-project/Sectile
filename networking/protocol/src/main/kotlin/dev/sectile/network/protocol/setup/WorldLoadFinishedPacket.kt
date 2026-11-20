package dev.sectile.network.protocol.setup

import dev.sectile.network.api.buffer.WriteBuffer
import dev.sectile.network.api.packet.OutboundPacket
import dev.sectile.network.api.packet.PacketDefinition
import dev.sectile.network.protocol.ProtocolPacketTable

/**
 * Outbound `WorldLoadFinished` packet (id 22, server -> client).
 *
 * Signals that world loading is complete. Empty payload.
 */
class WorldLoadFinishedPacket : OutboundPacket {

    override val definition: PacketDefinition
        get() = ProtocolPacketTable.WORLD_LOAD_FINISHED

    override fun write(buf: WriteBuffer) = Unit

    override fun size(): Int = 0
}
