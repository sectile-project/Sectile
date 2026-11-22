package dev.sectile.network.protocol.connection.outbound

import dev.sectile.network.api.buffer.WriteBuffer
import dev.sectile.network.api.packet.OutboundPacket
import dev.sectile.network.api.packet.PacketDefinition
import dev.sectile.network.protocol.DataSize
import dev.sectile.network.protocol.ProtocolPacketTable
import dev.sectile.network.protocol.connection.enums.DisconnectType
import dev.sectile.network.protocol.shared.text.FormattedMessage

class ServerDisconnectPacket(
    var reason: FormattedMessage? = null,
    var type: DisconnectType = DisconnectType.DISCONNECT,
) : OutboundPacket {

    override val definition: PacketDefinition = ProtocolPacketTable.SERVER_DISCONNECT

    override fun write(buf: WriteBuffer) {
        buf.writeByte(if (reason == null) 0 else 0x01)
        buf.writeByte(type.ordinal)
        reason?.write(buf)
    }

    override fun size(): Int = DataSize.BYTE * 2 + (reason?.computeSize() ?: 0)
}
