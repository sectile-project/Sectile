package dev.sectile.network.protocol.connection.outbound

import dev.sectile.network.api.buffer.WriteBuffer
import dev.sectile.network.api.packet.OutboundPacket
import dev.sectile.network.api.packet.PacketDefinition
import dev.sectile.network.protocol.ProtocolPacketTable

class NetworkTickPacket(
    var tickDurationMicros: Int = 0,
    var serverTick: Int = 0,
) : OutboundPacket {

    override val definition: PacketDefinition = ProtocolPacketTable.NETWORK_TICK

    override fun write(buf: WriteBuffer) {
        buf.writeInt(tickDurationMicros)
        buf.writeInt(serverTick)
    }

    override fun size(): Int = ProtocolPacketTable.NETWORK_TICK.maxSize
}
