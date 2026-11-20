package dev.sectile.network.protocol.world

import dev.sectile.network.api.buffer.WriteBuffer
import dev.sectile.network.api.packet.OutboundPacket
import dev.sectile.network.api.packet.PacketDefinition
import dev.sectile.network.protocol.ProtocolPacketTable

/**
 * Outbound `UpdateWeather` packet (id 149, server -> client).
 *
 * Sets the active weather index and transition duration in seconds.
 */
class UpdateWeatherPacket(
    var weatherIndex: Int = 0,
    var transitionSeconds: Float = 0f,
) : OutboundPacket {

    override val definition: PacketDefinition
        get() = ProtocolPacketTable.UPDATE_WEATHER

    override fun write(buf: WriteBuffer) {
        buf.writeInt(weatherIndex)
        buf.writeFloat(transitionSeconds)
    }

    override fun size(): Int = ProtocolPacketTable.UPDATE_WEATHER.maxSize
}
