package dev.sectile.network.protocol.world.outbound

import dev.sectile.network.api.buffer.WriteBuffer
import dev.sectile.network.api.packet.OutboundPacket
import dev.sectile.network.api.packet.PacketDefinition
import dev.sectile.network.protocol.DataSize
import dev.sectile.network.protocol.ProtocolPacketTable
import dev.sectile.network.protocol.shared.text.FormattedMessage

class WorldLoadProgressPacket(
    var status: FormattedMessage? = null,
    var percentComplete: Int = 0,
    var percentCompleteSubitem: Int = 0,
) : OutboundPacket {

    override val definition: PacketDefinition = ProtocolPacketTable.WORLD_LOAD_PROGRESS

    override fun write(buf: WriteBuffer) {
        buf.writeByte(if (status == null) 0 else 0x01)
        buf.writeInt(percentComplete)
        buf.writeInt(percentCompleteSubitem)
        status?.write(buf)
    }

    override fun size(): Int {
        val fixed = DataSize.BYTE + DataSize.INT * 2
        return fixed + (status?.computeSize() ?: 0)
    }
}
