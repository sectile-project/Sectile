package dev.sectile.network.protocol.auth

import dev.sectile.network.api.buffer.ReadBuffer
import dev.sectile.network.api.packet.InboundPacket
import dev.sectile.network.api.packet.PacketDefinition
import dev.sectile.network.protocol.ProtocolCodec
import dev.sectile.network.protocol.ProtocolPacketTable

/**
 * Inbound `PasswordResponse` packet (id 15, client -> server).
 *
 * Layout: nullability bitmask (1 byte) followed inline by the password hash without an offset
 * slot (single variable field).
 */
class PasswordResponsePacket(
    var hash: ByteArray? = null,
) : InboundPacket {

    override val definition: PacketDefinition
        get() = ProtocolPacketTable.PASSWORD_RESPONSE

    override fun read(buf: ReadBuffer) {
        val nullBits = buf.readUnsignedByte()
        hash = if (nullBits and 0x01 != 0) {
            ProtocolCodec.readVarByteArray(buf, MAX_HASH_LENGTH, "Hash")
        } else {
            null
        }
    }

    companion object {
        /** Maximum password hash byte length. */
        const val MAX_HASH_LENGTH: Int = 64
    }
}
