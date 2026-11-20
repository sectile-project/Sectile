package dev.sectile.network.protocol.world

import dev.sectile.network.api.buffer.WriteBuffer
import dev.sectile.network.api.packet.OutboundPacket
import dev.sectile.network.api.packet.PacketDefinition
import dev.sectile.network.protocol.ProtocolPacketTable

/**
 * Outbound `UpdatePostFxSettings` packet (id 361, server -> client).
 *
 * Configures client post-processing effects (bloom, sun shafts).
 */
class UpdatePostFxSettingsPacket(
    var globalIntensity: Float = 1f,
    var power: Float = 1f,
    var sunshaftScale: Float = 1f,
    var sunIntensity: Float = 1f,
    var sunshaftIntensity: Float = 1f,
) : OutboundPacket {

    override val definition: PacketDefinition
        get() = ProtocolPacketTable.UPDATE_POST_FX_SETTINGS

    override fun write(buf: WriteBuffer) {
        buf.writeFloat(globalIntensity)
        buf.writeFloat(power)
        buf.writeFloat(sunshaftScale)
        buf.writeFloat(sunIntensity)
        buf.writeFloat(sunshaftIntensity)
    }

    override fun size(): Int = ProtocolPacketTable.UPDATE_POST_FX_SETTINGS.maxSize
}
