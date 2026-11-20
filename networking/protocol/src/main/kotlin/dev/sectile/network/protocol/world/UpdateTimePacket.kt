package dev.sectile.network.protocol.world

import dev.sectile.network.api.buffer.WriteBuffer
import dev.sectile.network.api.packet.OutboundPacket
import dev.sectile.network.api.packet.PacketDefinition
import dev.sectile.network.protocol.ProtocolPacketTable
import dev.sectile.network.protocol.structures.InstantData

/**
 * Outbound `UpdateTime` packet (id 146, server -> client).
 *
 * Updates the current world game time inline without an offset slot.
 */
class UpdateTimePacket(
    var gameTime: InstantData? = null,
) : OutboundPacket {

    override val definition: PacketDefinition
        get() = ProtocolPacketTable.UPDATE_TIME

    override fun write(buf: WriteBuffer) {
        buf.writeByte(if (gameTime == null) 0 else 0x01)
        // InstantData has fixed length: zero-fill when omitted.
        gameTime?.write(buf) ?: buf.skip(InstantData.SIZE)
    }

    override fun size(): Int = ProtocolPacketTable.UPDATE_TIME.maxSize
}
