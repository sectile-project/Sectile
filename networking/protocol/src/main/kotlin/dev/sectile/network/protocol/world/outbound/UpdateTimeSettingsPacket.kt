package dev.sectile.network.protocol.world.outbound

import dev.sectile.network.api.buffer.WriteBuffer
import dev.sectile.network.api.packet.OutboundPacket
import dev.sectile.network.api.packet.PacketDefinition
import dev.sectile.network.protocol.ProtocolPacketTable

class UpdateTimeSettingsPacket(
    var daytimeDurationSeconds: Int = 600,
    var nighttimeDurationSeconds: Int = 600,
    var totalMoonPhases: Int = 8,
    var timePaused: Boolean = false,
) : OutboundPacket {

    override val definition: PacketDefinition = ProtocolPacketTable.UPDATE_TIME_SETTINGS

    override fun write(buf: WriteBuffer) {
        buf.writeInt(daytimeDurationSeconds)
        buf.writeInt(nighttimeDurationSeconds)
        buf.writeByte(totalMoonPhases)
        buf.writeBoolean(timePaused)
    }

    override fun size(): Int = ProtocolPacketTable.UPDATE_TIME_SETTINGS.maxSize
}
