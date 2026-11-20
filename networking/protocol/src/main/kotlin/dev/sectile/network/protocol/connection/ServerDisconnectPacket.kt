package dev.sectile.network.protocol.connection

import dev.sectile.network.api.buffer.WriteBuffer
import dev.sectile.network.api.packet.OutboundPacket
import dev.sectile.network.api.packet.PacketDefinition
import dev.sectile.network.protocol.ProtocolPacketTable
import dev.sectile.network.protocol.enums.DisconnectType
import dev.sectile.network.protocol.structures.FormattedMessage

/**
 * Outbound `ServerDisconnect` packet (id 2, server -> client).
 *
 * Layout: nullability bitmask (1 byte), disconnect type (1 byte), and the formatted reason
 * message inline without an offset slot (single variable field).
 */
class ServerDisconnectPacket(
    var reason: FormattedMessage? = null,
    var type: DisconnectType = DisconnectType.DISCONNECT,
) : OutboundPacket {

    override val definition: PacketDefinition
        get() = ProtocolPacketTable.SERVER_DISCONNECT

    override fun write(buf: WriteBuffer) {
        buf.writeByte(if (reason == null) 0 else 0x01)
        buf.writeByte(type.ordinal)
        reason?.write(buf)
    }

    override fun size(): Int = if (reason == null) 2 else 2 + reason!!.computeSize()
}
