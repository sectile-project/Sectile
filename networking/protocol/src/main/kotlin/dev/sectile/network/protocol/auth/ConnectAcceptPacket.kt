package dev.sectile.network.protocol.auth

import dev.sectile.network.api.buffer.WriteBuffer
import dev.sectile.network.api.packet.OutboundPacket
import dev.sectile.network.api.packet.PacketDefinition
import dev.sectile.network.protocol.ProtocolCodec
import dev.sectile.network.protocol.ProtocolPacketTable
import dev.sectile.network.protocol.VarInt

/**
 * Outbound `ConnectAccept` packet (id 14, server -> client).
 *
 * Layout: nullability bitmask (1 byte) followed inline by the optional password challenge,
 * with no offset slot (single variable field).
 */
class ConnectAcceptPacket(
    var passwordChallenge: ByteArray? = null,
) : OutboundPacket {

    override val definition: PacketDefinition
        get() = ProtocolPacketTable.CONNECT_ACCEPT

    override fun write(buf: WriteBuffer) {
        buf.writeByte(if (passwordChallenge == null) 0 else 0x01)
        if (passwordChallenge != null) {
            ProtocolCodec.writeVarByteArray(buf, passwordChallenge!!, MAX_PASSWORD_CHALLENGE_LENGTH)
        }
    }

    override fun size(): Int {
        val challenge = passwordChallenge ?: return 1
        return 1 + VarInt.sizeOf(challenge.size) + challenge.size
    }

    companion object {
        /** Maximum password challenge byte length. */
        const val MAX_PASSWORD_CHALLENGE_LENGTH: Int = 64
    }
}
