package dev.sectile.network.protocol.setup

import dev.sectile.network.api.buffer.WriteBuffer
import dev.sectile.network.api.packet.OutboundPacket
import dev.sectile.network.api.packet.PacketDefinition
import dev.sectile.network.protocol.ProtocolPacketTable
import dev.sectile.network.protocol.VarInt
import dev.sectile.network.protocol.enums.ClientFeature

/**
 * Outbound `UpdateFeatures` packet (id 31, server -> client).
 *
 * Enables or disables client features (mantling, crouching, sprinting, etc.).
 */
class UpdateFeaturesPacket(
    var features: MutableMap<ClientFeature, Boolean>? = null,
) : OutboundPacket {

    override val definition: PacketDefinition
        get() = ProtocolPacketTable.UPDATE_FEATURES

    override fun write(buf: WriteBuffer) {
        buf.writeByte(if (features == null) 0 else 0x01)
        features?.let { map ->
            VarInt.write(buf, map.size)
            map.forEach { (feature, enabled) ->
                buf.writeByte(feature.id)
                buf.writeByte(if (enabled) 1 else 0)
            }
        }
    }

    override fun size(): Int = 1 + (features?.let { VarInt.sizeOf(it.size) + it.size * 2 } ?: 0)
}
