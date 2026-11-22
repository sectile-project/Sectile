package dev.sectile.network.protocol.world.outbound

import dev.sectile.network.api.buffer.WriteBuffer
import dev.sectile.network.api.packet.OutboundPacket
import dev.sectile.network.api.packet.PacketDefinition
import dev.sectile.network.protocol.ProtocolPacketTable

class UpdateWorldMapSettingsPacket(
    var enabled: Boolean = true,
    var allowTeleportToCoordinates: Boolean = true,
    var allowTeleportToMarkers: Boolean = true,
    var allowShowOnMapToggle: Boolean = true,
    var allowCompassTrackingToggle: Boolean = true,
    var allowCreatingMapMarkers: Boolean = true,
    var allowRemovingOtherPlayersMarkers: Boolean = true,
    var defaultScale: Float = 1f,
    var minScale: Float = 0.25f,
    var maxScale: Float = 4f,
) : OutboundPacket {

    override val definition: PacketDefinition = ProtocolPacketTable.UPDATE_WORLD_MAP_SETTINGS

    override fun write(buf: WriteBuffer) {
        var bits = 0
        if (enabled) bits = bits or 0x01
        if (allowTeleportToCoordinates) bits = bits or 0x02
        if (allowTeleportToMarkers) bits = bits or 0x04
        if (allowShowOnMapToggle) bits = bits or 0x08
        if (allowCompassTrackingToggle) bits = bits or 0x10
        if (allowCreatingMapMarkers) bits = bits or 0x20
        if (allowRemovingOtherPlayersMarkers) bits = bits or 0x40
        buf.writeByte(bits)
        buf.writeFloat(defaultScale)
        buf.writeFloat(minScale)
        buf.writeFloat(maxScale)
    }

    override fun size(): Int = ProtocolPacketTable.UPDATE_WORLD_MAP_SETTINGS.maxSize
}
