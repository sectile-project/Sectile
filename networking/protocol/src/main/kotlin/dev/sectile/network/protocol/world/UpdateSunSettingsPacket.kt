package dev.sectile.network.protocol.world

import dev.sectile.network.api.buffer.WriteBuffer
import dev.sectile.network.api.packet.OutboundPacket
import dev.sectile.network.api.packet.PacketDefinition
import dev.sectile.network.protocol.ProtocolPacketTable

/**
 * Outbound `UpdateSunSettings` packet (id 360, server -> client).
 *
 * Configures sun position: height (`0` = horizon, `1` = zenith) and angle in radians.
 */
class UpdateSunSettingsPacket(
    var heightPercentage: Float = 0f,
    var angleRadians: Float = 0f,
) : OutboundPacket {

    override val definition: PacketDefinition
        get() = ProtocolPacketTable.UPDATE_SUN_SETTINGS

    override fun write(buf: WriteBuffer) {
        buf.writeFloat(heightPercentage)
        buf.writeFloat(angleRadians)
    }

    override fun size(): Int = ProtocolPacketTable.UPDATE_SUN_SETTINGS.maxSize
}
