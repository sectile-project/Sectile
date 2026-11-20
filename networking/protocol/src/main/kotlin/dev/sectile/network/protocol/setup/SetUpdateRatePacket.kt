package dev.sectile.network.protocol.setup

import dev.sectile.network.api.buffer.WriteBuffer
import dev.sectile.network.api.packet.OutboundPacket
import dev.sectile.network.api.packet.PacketDefinition
import dev.sectile.network.protocol.ProtocolPacketTable

/**
 * Outbound `SetUpdateRate` packet (id 29, server -> client).
 *
 * Sets the client update frequency in updates per second.
 */
class SetUpdateRatePacket(
    var updatesPerSecond: Int = 20,
) : OutboundPacket {

    override val definition: PacketDefinition
        get() = ProtocolPacketTable.SET_UPDATE_RATE

    override fun write(buf: WriteBuffer) {
        buf.writeInt(updatesPerSecond)
    }

    override fun size(): Int = ProtocolPacketTable.SET_UPDATE_RATE.maxSize
}
