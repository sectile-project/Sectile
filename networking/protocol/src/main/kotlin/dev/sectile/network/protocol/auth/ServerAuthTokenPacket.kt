package dev.sectile.network.protocol.auth

import dev.sectile.network.api.buffer.WriteBuffer
import dev.sectile.network.api.packet.OutboundPacket
import dev.sectile.network.api.packet.PacketDefinition
import dev.sectile.network.protocol.ProtocolCodec
import dev.sectile.network.protocol.ProtocolPacketTable
import dev.sectile.network.protocol.VarInt

/**
 * Outbound `ServerAuthToken` packet (id 13, server -> client).
 *
 * Layout: nullability bitmask (1 byte), two 4-byte offset slots, and the variable block
 * containing the server access token and password challenge.
 */
class ServerAuthTokenPacket(
    var serverAccessToken: String? = null,
    var passwordChallenge: ByteArray? = null,
) : OutboundPacket {

    override val definition: PacketDefinition
        get() = ProtocolPacketTable.SERVER_AUTH_TOKEN

    override fun write(buf: WriteBuffer) {
        var nullBits = 0
        if (serverAccessToken != null) nullBits = nullBits or 0x01
        if (passwordChallenge != null) nullBits = nullBits or 0x02

        buf.writeByte(nullBits)

        val tokenSlot = ProtocolCodec.reserveOffset(buf)
        val challengeSlot = ProtocolCodec.reserveOffset(buf)

        val varBlockStart = buf.index

        if (serverAccessToken == null) {
            buf.writeIntAt(tokenSlot, -1)
        } else {
            buf.writeIntAt(tokenSlot, buf.index - varBlockStart)
            ProtocolCodec.writeVarString(buf, serverAccessToken!!, MAX_SERVER_ACCESS_TOKEN_LENGTH)
        }

        if (passwordChallenge == null) {
            buf.writeIntAt(challengeSlot, -1)
        } else {
            buf.writeIntAt(challengeSlot, buf.index - varBlockStart)
            ProtocolCodec.writeVarByteArray(buf, passwordChallenge!!, MAX_PASSWORD_CHALLENGE_LENGTH)
        }
    }

    override fun size(): Int {
        var size = VARIABLE_BLOCK_START
        serverAccessToken?.let {
            val bytes = it.toByteArray(Charsets.UTF_8).size
            size += VarInt.sizeOf(bytes) + bytes
        }
        passwordChallenge?.let { size += VarInt.sizeOf(it.size) + it.size }
        return size
    }

    companion object {
        /** Fixed block length in bytes. */
        const val FIXED_BLOCK_SIZE: Int = 1

        /** Start offset of the variable block. */
        const val VARIABLE_BLOCK_START: Int = 9

        /** Maximum server access token string length. */
        const val MAX_SERVER_ACCESS_TOKEN_LENGTH: Int = 8_192

        /** Maximum password challenge byte length. */
        const val MAX_PASSWORD_CHALLENGE_LENGTH: Int = 64
    }
}
