package dev.sectile.network.protocol.auth

import dev.sectile.network.api.buffer.ReadBuffer
import dev.sectile.network.api.packet.InboundPacket
import dev.sectile.network.api.packet.PacketDefinition
import dev.sectile.network.protocol.ProtocolCodec
import dev.sectile.network.protocol.ProtocolPacketTable

/**
 * Inbound `AuthToken` packet (id 12, client -> server).
 *
 * Layout: nullability bitmask (1 byte), two 4-byte offset slots, and the variable block
 * containing the access token and the server authorization grant.
 */
class AuthTokenPacket(
    var accessToken: String? = null,
    var serverAuthGrant: String? = null,
) : InboundPacket {

    override val definition: PacketDefinition
        get() = ProtocolPacketTable.AUTH_TOKEN

    override fun read(buf: ReadBuffer) {
        val base = buf.index
        val nullBits = buf.readUnsignedByte()
        // Offset slots occupy `base + 1` through `base + 8`.
        val varBlockStart = base + VARIABLE_BLOCK_START
        buf.skipTo(varBlockStart)

        if (nullBits and 0x01 != 0) {
            ProtocolCodec.requireSlot(buf, base + 1, buf.index - varBlockStart, "AccessToken")
            accessToken = ProtocolCodec.readVarString(buf, MAX_ACCESS_TOKEN_LENGTH, "AccessToken")
        } else {
            ProtocolCodec.requireSlot(buf, base + 1, -1, "AccessToken")
            accessToken = null
        }

        if (nullBits and 0x02 != 0) {
            ProtocolCodec.requireSlot(buf, base + 5, buf.index - varBlockStart, "ServerAuthGrant")
            serverAuthGrant = ProtocolCodec.readVarString(
                buf,
                MAX_SERVER_AUTH_GRANT_LENGTH,
                "ServerAuthGrant",
            )
        } else {
            ProtocolCodec.requireSlot(buf, base + 5, -1, "ServerAuthGrant")
            serverAuthGrant = null
        }
    }

    companion object {
        /** Fixed block length in bytes. */
        const val FIXED_BLOCK_SIZE: Int = 1

        /** Start offset of the variable block. */
        const val VARIABLE_BLOCK_START: Int = 9

        /** Maximum access token string length. */
        const val MAX_ACCESS_TOKEN_LENGTH: Int = 8_192

        /** Maximum server authorization grant string length. */
        const val MAX_SERVER_AUTH_GRANT_LENGTH: Int = 4_096
    }
}
