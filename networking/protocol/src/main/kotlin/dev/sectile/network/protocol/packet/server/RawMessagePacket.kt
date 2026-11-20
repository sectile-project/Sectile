package dev.sectile.network.protocol.packet.server

import dev.sectile.network.api.buffer.WriteBuffer
import dev.sectile.network.api.packet.OutboundPacket
import dev.sectile.network.protocol.ProtocolFormatException
import dev.sectile.network.protocol.ProtocolPacketTable
import dev.sectile.network.protocol.VarInt

/**
 * Lightweight outbound `ServerMessage` packet (packet ID 210) for plain chat messages
 * containing optional [rawText] and/or [messageId] without allocating full `FormattedMessage` trees.
 */
class RawMessagePacket(
    val rawText: String? = null,
    val messageId: String? = null,
) : OutboundPacket {

    override val definition = ProtocolPacketTable.SERVER_MESSAGE

    override fun size(): Int {
        var total = SERVER_MESSAGE_HEADER_SIZE + FORMATTED_MESSAGE_FIXED_SIZE
        rawText?.let {
            val len = it.encodeValidated().size
            total += VarInt.sizeOf(len) + len
        }
        messageId?.let {
            val len = it.encodeValidated().size
            total += VarInt.sizeOf(len) + len
        }
        return total
    }

    override fun write(buf: WriteBuffer) {
        val rawBytes = rawText?.encodeValidated()
        val idBytes = messageId?.encodeValidated()

        // ServerMessage fixed header: nullBits (0x01 = message present), ChatType.Chat (0).
        buf.writeByte(0x01)
        buf.writeByte(0x00)

        // FormattedMessage header: 2 nullBits bytes + 6 boolean bytes (styles & markupEnabled = false).
        var nullBits0 = 0
        if (rawBytes != null) nullBits0 = nullBits0 or 0x20
        if (idBytes != null) nullBits0 = nullBits0 or 0x40
        buf.writeByte(nullBits0)
        buf.writeByte(0)
        buf.writeInt(0)
        buf.writeShort(0)

        // FormattedMessage offset slots (8 x 4 bytes = 32 bytes).
        val rawVarLen = if (rawBytes != null) VarInt.sizeOf(rawBytes.size) + rawBytes.size else 0
        buf.writeInt(if (rawBytes != null) 0 else -1)
        buf.writeInt(if (idBytes != null) rawVarLen else -1)
        buf.writeInt(-1)
        buf.writeInt(-1)
        buf.writeInt(-1)
        buf.writeInt(-1)
        buf.writeInt(-1)
        buf.writeInt(-1)

        // Variable block: rawText followed by messageId.
        if (rawBytes != null) writeVarBytes(buf, rawBytes)
        if (idBytes != null) writeVarBytes(buf, idBytes)
    }

    private fun writeVarBytes(buf: WriteBuffer, bytes: ByteArray) {
        VarInt.write(buf, bytes.size)
        buf.writeBytes(bytes)
    }

    private fun String.encodeValidated(): ByteArray {
        val bytes = toByteArray(Charsets.UTF_8)
        if (bytes.size > MAX_STRING_LENGTH) {
            throw ProtocolFormatException("String too long: ${bytes.size} > $MAX_STRING_LENGTH")
        }
        return bytes
    }

    companion object {
        const val MAX_STRING_LENGTH: Int = 4_096_000
        private const val SERVER_MESSAGE_HEADER_SIZE = 2
        private const val FORMATTED_MESSAGE_FIXED_SIZE = 40
    }
}
