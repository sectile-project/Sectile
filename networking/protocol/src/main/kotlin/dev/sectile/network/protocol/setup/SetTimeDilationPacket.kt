package dev.sectile.network.protocol.setup

import dev.sectile.network.api.buffer.WriteBuffer
import dev.sectile.network.api.packet.OutboundPacket
import dev.sectile.network.api.packet.PacketDefinition
import dev.sectile.network.protocol.ProtocolPacketTable

/**
 * Outbound `SetTimeDilation` packet (id 30, server -> client).
 *
 * Sets the client time dilation multiplier (`1.0` equals real time).
 */
class SetTimeDilationPacket(
    var timeDilation: Float = 1f,
) : OutboundPacket {

    override val definition: PacketDefinition
        get() = ProtocolPacketTable.SET_TIME_DILATION

    override fun write(buf: WriteBuffer) {
        buf.writeFloat(timeDilation)
    }

    override fun size(): Int = ProtocolPacketTable.SET_TIME_DILATION.maxSize
}
