package dev.sectile.network.protocol.auth.outbound

import dev.sectile.network.api.buffer.WriteBuffer
import dev.sectile.network.api.packet.OutboundPacket
import dev.sectile.network.api.packet.PacketDefinition
import dev.sectile.network.protocol.DataSize
import dev.sectile.network.protocol.ProtocolPacketTable

class ConnectAcceptPacket(
    var passwordChallenge: ByteArray? = null,
) : OutboundPacket {

    override val definition: PacketDefinition = ProtocolPacketTable.CONNECT_ACCEPT

    override fun write(buf: WriteBuffer) {
        val challenge = passwordChallenge
        buf.writeByte(if (challenge == null) 0 else 0x01)
        if (challenge != null) {
            buf.writeVarByteArray(challenge, MAX_PASSWORD_CHALLENGE_LENGTH)
        }
    }

    override fun size(): Int {
        val challenge = passwordChallenge ?: return DataSize.BYTE
        return DataSize.BYTE + DataSize.varBytesSize(challenge)
    }

    companion object {
        const val MAX_PASSWORD_CHALLENGE_LENGTH: Int = 64
    }
}
