package dev.sectile.network.protocol.auth

import dev.sectile.network.api.buffer.WriteBuffer
import dev.sectile.network.api.packet.OutboundPacket
import dev.sectile.network.api.packet.PacketDefinition
import dev.sectile.network.protocol.ProtocolCodec
import dev.sectile.network.protocol.ProtocolPacketTable
import dev.sectile.network.protocol.VarInt

/**
 * Outbound `AuthGrant` packet (id 11, server -> client).
 *
 * Layout: nullability bitmask (1 byte), two 4-byte offset slots, and the variable block
 * containing the authorization grant and the server identity token.
 */
class AuthGrantPacket(
    var authorizationGrant: String? = null,
    var serverIdentityToken: String? = null,
) : OutboundPacket {

    override val definition: PacketDefinition
        get() = ProtocolPacketTable.AUTH_GRANT

    override fun write(buf: WriteBuffer) {
        var nullBits = 0
        if (authorizationGrant != null) nullBits = nullBits or 0x01
        if (serverIdentityToken != null) nullBits = nullBits or 0x02

        buf.writeByte(nullBits)

        val grantSlot = ProtocolCodec.reserveOffset(buf)
        val tokenSlot = ProtocolCodec.reserveOffset(buf)

        val varBlockStart = buf.index

        if (authorizationGrant == null) {
            buf.writeIntAt(grantSlot, -1)
        } else {
            buf.writeIntAt(grantSlot, buf.index - varBlockStart)
            ProtocolCodec.writeVarString(buf, authorizationGrant!!, MAX_AUTHORIZATION_GRANT_LENGTH)
        }

        if (serverIdentityToken == null) {
            buf.writeIntAt(tokenSlot, -1)
        } else {
            buf.writeIntAt(tokenSlot, buf.index - varBlockStart)
            ProtocolCodec.writeVarString(buf, serverIdentityToken!!, MAX_SERVER_IDENTITY_TOKEN_LENGTH)
        }
    }

    override fun size(): Int {
        var size = VARIABLE_BLOCK_START
        authorizationGrant?.let {
            val bytes = it.toByteArray(Charsets.UTF_8).size
            size += VarInt.sizeOf(bytes) + bytes
        }
        serverIdentityToken?.let {
            val bytes = it.toByteArray(Charsets.UTF_8).size
            size += VarInt.sizeOf(bytes) + bytes
        }
        return size
    }

    companion object {
        /** Fixed block length in bytes. */
        const val FIXED_BLOCK_SIZE: Int = 1

        /** Start offset of the variable block. */
        const val VARIABLE_BLOCK_START: Int = 9

        /** Maximum authorization grant string length. */
        const val MAX_AUTHORIZATION_GRANT_LENGTH: Int = 4_096

        /** Maximum server identity token string length. */
        const val MAX_SERVER_IDENTITY_TOKEN_LENGTH: Int = 8_192
    }
}
