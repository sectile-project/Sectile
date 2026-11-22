package dev.sectile.network.protocol.chat.inbound

import dev.sectile.network.api.buffer.ReadBuffer
import dev.sectile.network.api.packet.InboundPacket
import dev.sectile.network.api.packet.PacketDefinition
import dev.sectile.network.protocol.ProtocolPacketTable

class ChatMessagePacket(
    var message: String? = null,
) : InboundPacket {

    override val definition: PacketDefinition = ProtocolPacketTable.CHAT_MESSAGE

    override fun read(buf: ReadBuffer) {
        val nullBits = buf.readUnsignedByte()
        message = if (nullBits and 0x01 != 0) {
            buf.readVarString(MAX_MESSAGE_LENGTH, "Message")
        } else {
            null
        }
    }

    companion object {
        const val MAX_MESSAGE_LENGTH: Int = 255
    }
}
