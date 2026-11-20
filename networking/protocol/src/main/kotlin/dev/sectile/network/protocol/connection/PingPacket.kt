package dev.sectile.network.protocol.connection

import dev.sectile.network.api.buffer.WriteBuffer
import dev.sectile.network.api.packet.OutboundPacket
import dev.sectile.network.api.packet.PacketDefinition
import dev.sectile.network.protocol.ProtocolPacketTable
import dev.sectile.network.protocol.structures.InstantData

/**
 * Outbound `Ping` packet (id 3, server -> client).
 *
 * Fixed 28-byte payload: ping id (int LE), timestamp (12 bytes), and three latency metrics
 * (int LE).
 */
class PingPacket(
    var id: Int = 0,
    var time: InstantData = InstantData(),
    var lastPingValueRaw: Int = 0,
    var lastPingValueDirect: Int = 0,
    var lastPingValueTick: Int = 0,
) : OutboundPacket {

    override val definition: PacketDefinition
        get() = ProtocolPacketTable.PING

    override fun write(buf: WriteBuffer) {
        buf.writeInt(id)
        time.write(buf)
        buf.writeInt(lastPingValueRaw)
        buf.writeInt(lastPingValueDirect)
        buf.writeInt(lastPingValueTick)
    }

    override fun size(): Int = ProtocolPacketTable.PING.maxSize
}
