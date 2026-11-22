package dev.sectile.network.protocol.auth.inbound

import dev.sectile.network.api.buffer.ReadBuffer
import dev.sectile.network.api.buffer.readSlot
import dev.sectile.network.api.packet.InboundPacket
import dev.sectile.network.api.packet.PacketDefinition
import dev.sectile.network.protocol.ProtocolPacketTable

class AuthTokenPacket(
    var accessToken: String? = null,
    var serverAuthGrant: String? = null,
) : InboundPacket {

    override val definition: PacketDefinition = ProtocolPacketTable.AUTH_TOKEN

    override fun read(buf: ReadBuffer) {
        val base = buf.index
        val nullBits = buf.readUnsignedByte()
        val varBlockStart = base + VARIABLE_BLOCK_START
        buf.skipTo(varBlockStart)

        accessToken = buf.readSlot(base + 1, varBlockStart, nullBits and 0x01 != 0, "AccessToken") {
            buf.readVarString(MAX_ACCESS_TOKEN_LENGTH, "AccessToken")
        }
        serverAuthGrant = buf.readSlot(base + 5, varBlockStart, nullBits and 0x02 != 0, "ServerAuthGrant") {
            buf.readVarString(MAX_SERVER_AUTH_GRANT_LENGTH, "ServerAuthGrant")
        }
    }

    companion object {
        const val FIXED_BLOCK_SIZE: Int = 1
        const val VARIABLE_BLOCK_START: Int = 9
        const val MAX_ACCESS_TOKEN_LENGTH: Int = 8_192
        const val MAX_SERVER_AUTH_GRANT_LENGTH: Int = 4_096
    }
}
