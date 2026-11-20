package dev.sectile.network.protocol.connection

import dev.sectile.network.api.buffer.WriteBuffer
import dev.sectile.network.api.packet.OutboundPacket
import dev.sectile.network.api.packet.PacketDefinition
import dev.sectile.network.protocol.ProtocolPacketTable

/**
 * Outbound `NetworkTick` packet (id 5, server -> client).
 *
 * Fixed 8-byte payload: tick duration in microseconds (uint LE) and server tick (uint LE).
 */
class NetworkTickPacket(
    var tickDurationMicros: Int = 0,
    var serverTick: Int = 0,
) : OutboundPacket {

    override val definition: PacketDefinition
        get() = ProtocolPacketTable.NETWORK_TICK

    override fun write(buf: WriteBuffer) {
        buf.writeInt(tickDurationMicros)
        buf.writeInt(serverTick)
    }

    override fun size(): Int = ProtocolPacketTable.NETWORK_TICK.maxSize
}
