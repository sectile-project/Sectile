package dev.sectile.network.protocol.connection

import dev.sectile.network.api.buffer.ReadBuffer
import dev.sectile.network.api.packet.InboundPacket
import dev.sectile.network.api.packet.PacketDefinition
import dev.sectile.network.protocol.ProtocolFormatException
import dev.sectile.network.protocol.ProtocolPacketTable
import dev.sectile.network.protocol.enums.ClientDisconnectReason
import dev.sectile.network.protocol.enums.DisconnectType

/**
 * Inbound `ClientDisconnect` packet (id 1, client -> server).
 *
 * Fixed 2-byte payload: disconnect reason (1 byte) and disconnect type (1 byte).
 */
class ClientDisconnectPacket(
    var reason: ClientDisconnectReason = ClientDisconnectReason.PLAYER_LEAVE,
    var type: DisconnectType = DisconnectType.DISCONNECT,
) : InboundPacket {

    override val definition: PacketDefinition
        get() = ProtocolPacketTable.CLIENT_DISCONNECT

    override fun read(buf: ReadBuffer) {
        val reasonOrdinal = buf.readUnsignedByte()
        reason = ClientDisconnectReason.of(reasonOrdinal)
            ?: throw ProtocolFormatException("Invalid enum value 'ClientDisconnectReason': $reasonOrdinal")
        val typeOrdinal = buf.readUnsignedByte()
        type = DisconnectType.of(typeOrdinal)
            ?: throw ProtocolFormatException("Invalid enum value 'DisconnectType': $typeOrdinal")
    }
}
