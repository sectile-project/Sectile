package dev.sectile.network.protocol.world.outbound

import dev.sectile.network.api.buffer.WriteBuffer
import dev.sectile.network.api.packet.OutboundPacket
import dev.sectile.network.api.packet.PacketDefinition
import dev.sectile.network.protocol.ProtocolPacketTable

class UpdateSunSettingsPacket(
    var heightPercentage: Float = 0f,
    var angleRadians: Float = 0f,
) : OutboundPacket {

    override val definition: PacketDefinition = ProtocolPacketTable.UPDATE_SUN_SETTINGS

    override fun write(buf: WriteBuffer) {
        buf.writeFloat(heightPercentage)
        buf.writeFloat(angleRadians)
    }

    override fun size(): Int = ProtocolPacketTable.UPDATE_SUN_SETTINGS.maxSize
}
