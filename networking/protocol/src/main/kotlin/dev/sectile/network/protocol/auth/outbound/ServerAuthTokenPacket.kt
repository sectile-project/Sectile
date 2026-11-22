package dev.sectile.network.protocol.auth.outbound

import dev.sectile.network.api.buffer.WriteBuffer
import dev.sectile.network.api.buffer.writeSlot
import dev.sectile.network.api.packet.OutboundPacket
import dev.sectile.network.api.packet.PacketDefinition
import dev.sectile.network.protocol.DataSize
import dev.sectile.network.protocol.ProtocolPacketTable

class ServerAuthTokenPacket(
    var serverAccessToken: String? = null,
    var passwordChallenge: ByteArray? = null,
) : OutboundPacket {

    override val definition: PacketDefinition = ProtocolPacketTable.SERVER_AUTH_TOKEN

    override fun write(buf: WriteBuffer) {
        var nullBits = 0
        if (serverAccessToken != null) nullBits = nullBits or 0x01
        if (passwordChallenge != null) nullBits = nullBits or 0x02

        buf.writeByte(nullBits)

        val tokenSlot = buf.reserveOffset()
        val challengeSlot = buf.reserveOffset()

        val varBlockStart = buf.index

        buf.writeSlot(tokenSlot, varBlockStart, serverAccessToken) {
            buf.writeVarString(it, MAX_SERVER_ACCESS_TOKEN_LENGTH)
        }
        buf.writeSlot(challengeSlot, varBlockStart, passwordChallenge) {
            buf.writeVarByteArray(it, MAX_PASSWORD_CHALLENGE_LENGTH)
        }
    }

    override fun size(): Int {
        var size = VARIABLE_BLOCK_START
        serverAccessToken?.let { size += DataSize.varStringSize(it) }
        passwordChallenge?.let { size += DataSize.varBytesSize(it) }
        return size
    }

    companion object {
        const val FIXED_BLOCK_SIZE: Int = 1
        const val VARIABLE_BLOCK_START: Int = 9
        const val MAX_SERVER_ACCESS_TOKEN_LENGTH: Int = 8_192
        const val MAX_PASSWORD_CHALLENGE_LENGTH: Int = 64
    }
}
