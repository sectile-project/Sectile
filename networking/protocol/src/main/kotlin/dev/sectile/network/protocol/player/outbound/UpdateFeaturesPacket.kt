package dev.sectile.network.protocol.player.outbound

import dev.sectile.network.api.buffer.WriteBuffer
import dev.sectile.network.api.packet.OutboundPacket
import dev.sectile.network.api.packet.PacketDefinition
import dev.sectile.network.protocol.DataSize
import dev.sectile.network.protocol.ProtocolPacketTable
import dev.sectile.network.protocol.player.enums.ClientFeature

class UpdateFeaturesPacket(
    var features: MutableMap<ClientFeature, Boolean>? = null,
) : OutboundPacket {

    override val definition: PacketDefinition = ProtocolPacketTable.UPDATE_FEATURES

    override fun write(buf: WriteBuffer) {
        buf.writeByte(if (features == null) 0 else 0x01)
        features?.let { map ->
            buf.writeVarInt(map.size)
            map.forEach { (feature, enabled) ->
                buf.writeByte(feature.id)
                buf.writeBoolean(enabled)
            }
        }
    }

    override fun size(): Int = DataSize.BYTE + (features?.let { DataSize.varListSize(it.size, DataSize.BYTE + DataSize.BOOLEAN) } ?: 0)
}
