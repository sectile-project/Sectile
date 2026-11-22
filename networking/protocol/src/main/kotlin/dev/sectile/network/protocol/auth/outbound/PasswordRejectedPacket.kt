package dev.sectile.network.protocol.auth.outbound

import dev.sectile.network.api.buffer.WriteBuffer
import dev.sectile.network.api.packet.OutboundPacket
import dev.sectile.network.api.packet.PacketDefinition
import dev.sectile.network.protocol.DataSize
import dev.sectile.network.protocol.ProtocolPacketTable

class PasswordRejectedPacket(
    var attemptsRemaining: Int = 0,
    var newChallenge: ByteArray? = null,
) : OutboundPacket {

    override val definition: PacketDefinition = ProtocolPacketTable.PASSWORD_REJECTED

    override fun write(buf: WriteBuffer) {
        val challenge = newChallenge
        buf.writeByte(if (challenge == null) 0 else 0x01)
        buf.writeInt(attemptsRemaining)
        if (challenge != null) {
            buf.writeVarByteArray(challenge, MAX_NEW_CHALLENGE_LENGTH)
        }
    }

    override fun size(): Int {
        val fixed = DataSize.BYTE + DataSize.INT
        val challenge = newChallenge ?: return fixed
        return fixed + DataSize.varBytesSize(challenge)
    }

    companion object {
        const val MAX_NEW_CHALLENGE_LENGTH: Int = 64
    }
}
