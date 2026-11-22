package dev.sectile.network.protocol.world.outbound

import dev.sectile.network.api.buffer.WriteBuffer
import dev.sectile.network.api.packet.OutboundPacket
import dev.sectile.network.api.packet.PacketDefinition
import dev.sectile.network.protocol.ProtocolPacketTable
import dev.sectile.network.protocol.shared.InstantData

class UpdateTimePacket(
    var gameTime: InstantData? = null,
) : OutboundPacket {

    override val definition: PacketDefinition = ProtocolPacketTable.UPDATE_TIME

    override fun write(buf: WriteBuffer) {
        buf.writeByte(if (gameTime == null) 0 else 0x01)
        gameTime?.write(buf) ?: buf.skip(InstantData.SIZE)
    }

    override fun size(): Int = ProtocolPacketTable.UPDATE_TIME.maxSize
}
