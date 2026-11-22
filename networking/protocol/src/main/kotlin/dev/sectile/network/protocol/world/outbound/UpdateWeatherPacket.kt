package dev.sectile.network.protocol.world.outbound

import dev.sectile.network.api.buffer.WriteBuffer
import dev.sectile.network.api.packet.OutboundPacket
import dev.sectile.network.api.packet.PacketDefinition
import dev.sectile.network.protocol.ProtocolPacketTable

class UpdateWeatherPacket(
    var weatherIndex: Int = 0,
    var transitionSeconds: Float = 0f,
) : OutboundPacket {

    override val definition: PacketDefinition = ProtocolPacketTable.UPDATE_WEATHER

    override fun write(buf: WriteBuffer) {
        buf.writeInt(weatherIndex)
        buf.writeFloat(transitionSeconds)
    }

    override fun size(): Int = ProtocolPacketTable.UPDATE_WEATHER.maxSize
}
