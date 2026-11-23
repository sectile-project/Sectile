package dev.sectile.network.protocol.chat.outbound

import dev.sectile.network.api.buffer.WriteBuffer
import dev.sectile.network.api.packet.OutboundPacket
import dev.sectile.network.api.packet.PacketDefinition
import dev.sectile.network.protocol.DataSize
import dev.sectile.network.protocol.ProtocolPacketTable

private const val SERVER_MESSAGE_HEADER_SIZE = 2
private const val FORMATTED_MESSAGE_FIXED_SIZE = 40

/**
 * Lightweight outbound `ServerMessage` packet for plain chat messages
 * without allocating full `FormattedMessage` trees.
 */
class RawMessagePacket(
    val rawText: String? = null,
    val messageId: String? = null,
) : OutboundPacket {

    override val definition: PacketDefinition = ProtocolPacketTable.SERVER_MESSAGE

    override fun size(): Int {
        var total = SERVER_MESSAGE_HEADER_SIZE + FORMATTED_MESSAGE_FIXED_SIZE
        rawText?.let { total += DataSize.varBytesSize(it.encodeValidatedUtf8()) }
        messageId?.let { total += DataSize.varBytesSize(it.encodeValidatedUtf8()) }
        return total
    }

    override fun write(buf: WriteBuffer) {
        val rawBytes = rawText?.encodeValidatedUtf8()
        val idBytes = messageId?.encodeValidatedUtf8()

        // ServerMessage fixed header: nullBits (0x01 = message present), ChatType.Chat (0).
        buf.writeByte(0x01)
        buf.writeByte(0x00)

        var nullBits0 = 0
        if (rawBytes != null) nullBits0 = nullBits0 or 0x20
        if (idBytes != null) nullBits0 = nullBits0 or 0x40
        buf.writeByte(nullBits0)
        buf.writeByte(0)
        buf.writeInt(0)
        buf.writeShort(0)

        val rawVarLen = rawBytes?.let(DataSize::varBytesSize) ?: 0
        buf.writeInt(if (rawBytes != null) 0 else -1)
        buf.writeInt(if (idBytes != null) rawVarLen else -1)
        buf.writeInt(-1)
        buf.writeInt(-1)
        buf.writeInt(-1)
        buf.writeInt(-1)
        buf.writeInt(-1)
        buf.writeInt(-1)

        if (rawBytes != null) buf.writeVarBytes(rawBytes)
        if (idBytes != null) buf.writeVarBytes(idBytes)
    }
}
