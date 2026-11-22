package dev.sectile.network.protocol.player.outbound

import dev.sectile.network.api.buffer.WriteBuffer
import dev.sectile.network.api.packet.OutboundPacket
import dev.sectile.network.api.packet.PacketDefinition
import dev.sectile.network.protocol.ProtocolPacketTable

class SetUpdateRatePacket(
    var updatesPerSecond: Int = 20,
) : OutboundPacket {

    override val definition: PacketDefinition = ProtocolPacketTable.SET_UPDATE_RATE

    override fun write(buf: WriteBuffer) {
        buf.writeInt(updatesPerSecond)
    }

    override fun size(): Int = ProtocolPacketTable.SET_UPDATE_RATE.maxSize
}
