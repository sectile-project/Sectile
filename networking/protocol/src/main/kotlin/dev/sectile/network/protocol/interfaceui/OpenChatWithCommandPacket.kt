package dev.sectile.network.protocol.interfaceui

import dev.sectile.network.api.buffer.ReadBuffer
import dev.sectile.network.api.buffer.WriteBuffer
import dev.sectile.network.api.packet.InboundPacket
import dev.sectile.network.api.packet.OutboundPacket
import dev.sectile.network.api.packet.PacketDefinition
import dev.sectile.network.protocol.ProtocolCodec
import dev.sectile.network.protocol.ProtocolPacketTable
import dev.sectile.network.protocol.VarInt

/**
 * Outbound `OpenChatWithCommand` packet (id 234, server -> client).
 *
 * Opens the client chat prompt pre-filled with [command].
 */
class OpenChatWithCommandPacket(
    var command: String? = null,
) : OutboundPacket,
    InboundPacket {

    override val definition: PacketDefinition
        get() = ProtocolPacketTable.OPEN_CHAT_WITH_COMMAND

    override fun read(buf: ReadBuffer) {
        val nullBits = buf.readUnsignedByte()
        command = if (nullBits and 0x01 != 0) {
            ProtocolCodec.readVarString(buf, MAX_COMMAND_LENGTH, "Command")
        } else {
            null
        }
    }

    override fun write(buf: WriteBuffer) {
        val current = command
        if (current == null) {
            buf.writeByte(0)
            return
        }
        buf.writeByte(0x01)
        ProtocolCodec.writeVarString(buf, current, MAX_COMMAND_LENGTH)
    }

    override fun size(): Int {
        var total = VARIABLE_BLOCK_START
        command?.let {
            val bytes = it.toByteArray(Charsets.UTF_8).size
            total += VarInt.sizeOf(bytes) + bytes
        }
        return total
    }

    companion object {
        const val FIXED_BLOCK_SIZE: Int = 1
        const val VARIABLE_BLOCK_START: Int = 1
        const val MAX_COMMAND_LENGTH: Int = ProtocolCodec.MAX_STRING_LENGTH
    }
}
