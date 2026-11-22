package dev.sectile.network.protocol.connection.outbound

import dev.sectile.network.api.buffer.WriteBuffer
import dev.sectile.network.api.packet.OutboundPacket
import dev.sectile.network.api.packet.PacketDefinition
import dev.sectile.network.protocol.ProtocolPacketTable
import dev.sectile.network.protocol.shared.InstantData

class PingPacket(
    var id: Int = 0,
    var time: InstantData = InstantData(),
    var lastPingValueRaw: Int = 0,
    var lastPingValueDirect: Int = 0,
    var lastPingValueTick: Int = 0,
) : OutboundPacket {

    override val definition: PacketDefinition = ProtocolPacketTable.PING

    override fun write(buf: WriteBuffer) {
        buf.writeInt(id)
        time.write(buf)
        buf.writeInt(lastPingValueRaw)
        buf.writeInt(lastPingValueDirect)
        buf.writeInt(lastPingValueTick)
    }

    override fun size(): Int = ProtocolPacketTable.PING.maxSize
}
