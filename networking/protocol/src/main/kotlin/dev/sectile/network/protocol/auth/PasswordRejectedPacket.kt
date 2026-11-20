package dev.sectile.network.protocol.auth

import dev.sectile.network.api.buffer.WriteBuffer
import dev.sectile.network.api.packet.OutboundPacket
import dev.sectile.network.api.packet.PacketDefinition
import dev.sectile.network.protocol.ProtocolCodec
import dev.sectile.network.protocol.ProtocolPacketTable
import dev.sectile.network.protocol.VarInt

/**
 * Outbound `PasswordRejected` packet (id 17, server -> client).
 *
 * Layout: nullability bitmask (1 byte), remaining attempts (int LE), and the new password
 * challenge inline without an offset slot (single variable field).
 */
class PasswordRejectedPacket(
    var attemptsRemaining: Int = 0,
    var newChallenge: ByteArray? = null,
) : OutboundPacket {

    override val definition: PacketDefinition
        get() = ProtocolPacketTable.PASSWORD_REJECTED

    override fun write(buf: WriteBuffer) {
        buf.writeByte(if (newChallenge == null) 0 else 0x01)
        buf.writeInt(attemptsRemaining)
        if (newChallenge != null) {
            ProtocolCodec.writeVarByteArray(buf, newChallenge!!, MAX_NEW_CHALLENGE_LENGTH)
        }
    }

    override fun size(): Int {
        val challenge = newChallenge ?: return 5
        return 5 + VarInt.sizeOf(challenge.size) + challenge.size
    }

    companion object {
        /** Maximum new challenge byte length. */
        const val MAX_NEW_CHALLENGE_LENGTH: Int = 64
    }
}
