package dev.sectile.network.protocol.auth.outbound

import dev.sectile.network.api.buffer.WriteBuffer
import dev.sectile.network.api.buffer.writeSlot
import dev.sectile.network.api.packet.OutboundPacket
import dev.sectile.network.api.packet.PacketDefinition
import dev.sectile.network.protocol.DataSize
import dev.sectile.network.protocol.ProtocolPacketTable

class AuthGrantPacket(
    var authorizationGrant: String? = null,
    var serverIdentityToken: String? = null,
) : OutboundPacket {

    override val definition: PacketDefinition = ProtocolPacketTable.AUTH_GRANT

    override fun write(buf: WriteBuffer) {
        var nullBits = 0
        if (authorizationGrant != null) nullBits = nullBits or 0x01
        if (serverIdentityToken != null) nullBits = nullBits or 0x02

        buf.writeByte(nullBits)

        val grantSlot = buf.reserveOffset()
        val tokenSlot = buf.reserveOffset()

        val varBlockStart = buf.index

        buf.writeSlot(grantSlot, varBlockStart, authorizationGrant) {
            buf.writeVarString(it, MAX_AUTHORIZATION_GRANT_LENGTH)
        }
        buf.writeSlot(tokenSlot, varBlockStart, serverIdentityToken) {
            buf.writeVarString(it, MAX_SERVER_IDENTITY_TOKEN_LENGTH)
        }
    }

    override fun size(): Int {
        var size = VARIABLE_BLOCK_START
        authorizationGrant?.let { size += DataSize.varStringSize(it) }
        serverIdentityToken?.let { size += DataSize.varStringSize(it) }
        return size
    }

    companion object {
        const val FIXED_BLOCK_SIZE: Int = 1
        const val VARIABLE_BLOCK_START: Int = 9
        const val MAX_AUTHORIZATION_GRANT_LENGTH: Int = 4_096
        const val MAX_SERVER_IDENTITY_TOKEN_LENGTH: Int = 8_192
    }
}
