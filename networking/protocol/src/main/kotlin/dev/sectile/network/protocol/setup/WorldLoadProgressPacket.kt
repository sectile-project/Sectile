package dev.sectile.network.protocol.setup

import dev.sectile.network.api.buffer.WriteBuffer
import dev.sectile.network.api.packet.OutboundPacket
import dev.sectile.network.api.packet.PacketDefinition
import dev.sectile.network.protocol.ProtocolPacketTable
import dev.sectile.network.protocol.structures.FormattedMessage

/**
 * Outbound `WorldLoadProgress` packet (id 21, server -> client).
 *
 * Layout: nullability bitmask (1 byte), completion percentage (int LE), subitem completion
 * percentage (int LE), and the status message inline without an offset slot.
 */
class WorldLoadProgressPacket(
    var status: FormattedMessage? = null,
    var percentComplete: Int = 0,
    var percentCompleteSubitem: Int = 0,
) : OutboundPacket {

    override val definition: PacketDefinition
        get() = ProtocolPacketTable.WORLD_LOAD_PROGRESS

    override fun write(buf: WriteBuffer) {
        buf.writeByte(if (status == null) 0 else 0x01)
        buf.writeInt(percentComplete)
        buf.writeInt(percentCompleteSubitem)
        status?.write(buf)
    }

    override fun size(): Int = if (status == null) 9 else 9 + status!!.computeSize()
}
