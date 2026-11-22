package dev.sectile.network.protocol.player.outbound

import dev.sectile.network.api.buffer.WriteBuffer
import dev.sectile.network.api.packet.OutboundPacket
import dev.sectile.network.api.packet.PacketDefinition
import dev.sectile.network.protocol.ProtocolPacketTable

class SetTimeDilationPacket(
    var timeDilation: Float = 1f,
) : OutboundPacket {

    override val definition: PacketDefinition = ProtocolPacketTable.SET_TIME_DILATION

    override fun write(buf: WriteBuffer) {
        buf.writeFloat(timeDilation)
    }

    override fun size(): Int = ProtocolPacketTable.SET_TIME_DILATION.maxSize
}
