package dev.sectile.network.protocol.chat.outbound

import dev.sectile.network.api.buffer.WriteBuffer
import dev.sectile.network.api.packet.OutboundPacket
import dev.sectile.network.api.packet.PacketDefinition
import dev.sectile.network.protocol.DataSize
import dev.sectile.network.protocol.ProtocolPacketTable

class OpenChatWithCommandPacket(
    var command: String? = null,
) : OutboundPacket {

    override val definition: PacketDefinition = ProtocolPacketTable.OPEN_CHAT_WITH_COMMAND

    override fun write(buf: WriteBuffer) {
        val current = command
        if (current == null) {
            buf.writeByte(0)
            return
        }
        buf.writeByte(0x01)
        buf.writeVarString(current, MAX_COMMAND_LENGTH)
    }

    override fun size(): Int = VARIABLE_BLOCK_START + (command?.let(DataSize::varStringSize) ?: 0)

    companion object {
        const val FIXED_BLOCK_SIZE: Int = 1
        const val VARIABLE_BLOCK_START: Int = 1
        const val MAX_COMMAND_LENGTH: Int = DataSize.MAX_STRING_LENGTH
    }
}
