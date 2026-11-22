package dev.sectile.network.protocol.auth.inbound

import dev.sectile.network.api.buffer.ReadBuffer
import dev.sectile.network.api.packet.InboundPacket
import dev.sectile.network.api.packet.PacketDefinition
import dev.sectile.network.protocol.ProtocolPacketTable

class PasswordResponsePacket(
    var hash: ByteArray? = null,
) : InboundPacket {

    override val definition: PacketDefinition = ProtocolPacketTable.PASSWORD_RESPONSE

    override fun read(buf: ReadBuffer) {
        val nullBits = buf.readUnsignedByte()
        hash = if (nullBits and 0x01 != 0) {
            buf.readVarByteArray(MAX_HASH_LENGTH, "Hash")
        } else {
            null
        }
    }

    companion object {
        const val MAX_HASH_LENGTH: Int = 64
    }
}
