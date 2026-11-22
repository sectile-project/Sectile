package dev.sectile.network.protocol.chat.outbound

import dev.sectile.network.api.buffer.WriteBuffer
import dev.sectile.network.api.buffer.writeSlot
import dev.sectile.network.api.packet.OutboundPacket
import dev.sectile.network.api.packet.PacketDefinition
import dev.sectile.network.protocol.DataSize
import dev.sectile.network.protocol.ProtocolFormatException
import dev.sectile.network.protocol.ProtocolPacketTable
import dev.sectile.network.protocol.shared.contracts.FormattedMessageData

class ServerMessagePacket(
    var message: FormattedMessageData? = null,
    var chatType: Byte = 0,
) : OutboundPacket {

    override val definition: PacketDefinition = ProtocolPacketTable.SERVER_MESSAGE

    override fun size(): Int {
        val root = message ?: return HEADER_SIZE
        return HEADER_SIZE + computeMessageSize(root, 0)
    }

    override fun write(buf: WriteBuffer) {
        val root = message
        if (root == null) {
            buf.writeByte(0)
            buf.writeByte(chatType)
            return
        }
        buf.writeByte(0x01)
        buf.writeByte(chatType)
        writeMessage(buf, root, 0)
    }

    private fun computeMessageSize(node: FormattedMessageData, depth: Int): Int {
        if (depth > MAX_NESTING_DEPTH) {
            throw ProtocolFormatException("FormattedMessage depth exceeded $MAX_NESTING_DEPTH")
        }

        var total = FORMATTED_MESSAGE_FIXED_SIZE
        node.rawText?.let { total += DataSize.varBytesSize(it.encodeValidatedUtf8()) }
        node.messageId?.let { total += DataSize.varBytesSize(it.encodeValidatedUtf8()) }
        val children = node.children
        if (children.isNotEmpty()) {
            if (children.size > DataSize.MAX_ARRAY_LENGTH) {
                throw ProtocolFormatException("Array too long in field 'Children': ${children.size}")
            }
            total += DataSize.varListSize(children) { computeMessageSize(it, depth + 1) }
        }
        if (node.colorRgb >= 0) {
            total += HEX_COLOR_WIRE_SIZE
        }
        node.link?.let { total += DataSize.varBytesSize(it.encodeValidatedUtf8()) }
        return total
    }

    private fun writeMessage(buf: WriteBuffer, node: FormattedMessageData, depth: Int) {
        if (depth > MAX_NESTING_DEPTH) {
            throw ProtocolFormatException("FormattedMessage depth exceeded $MAX_NESTING_DEPTH")
        }

        val rawBytes = node.rawText?.encodeValidatedUtf8()
        val idBytes = node.messageId?.encodeValidatedUtf8()
        val children = node.children
        val hasChildren = children.isNotEmpty()
        val hasColor = node.colorRgb >= 0
        val linkBytes = node.link?.encodeValidatedUtf8()

        var nullBits0 = 0
        if (node.bold) nullBits0 = nullBits0 or 0x01
        if (node.italic) nullBits0 = nullBits0 or 0x02
        if (node.monospace) nullBits0 = nullBits0 or 0x04
        if (node.underline) nullBits0 = nullBits0 or 0x08
        if (node.strikethrough) nullBits0 = nullBits0 or 0x10
        if (rawBytes != null) nullBits0 = nullBits0 or 0x20
        if (idBytes != null) nullBits0 = nullBits0 or 0x40
        if (hasChildren) nullBits0 = nullBits0 or 0x80

        var nullBits1 = 0
        if (hasColor) nullBits1 = nullBits1 or 0x04
        if (linkBytes != null) nullBits1 = nullBits1 or 0x08

        buf.writeByte(nullBits0)
        buf.writeByte(nullBits1)
        buf.writeBoolean(node.bold)
        buf.writeBoolean(node.italic)
        buf.writeBoolean(node.monospace)
        buf.writeBoolean(node.underline)
        buf.writeBoolean(node.strikethrough)
        buf.writeBoolean(node.markupEnabled)

        val rawSlot = buf.reserveOffset()
        val idSlot = buf.reserveOffset()
        val childrenSlot = buf.reserveOffset()
        buf.writeInt(-1) // params
        buf.writeInt(-1) // messageParams
        val colorSlot = buf.reserveOffset()
        val linkSlot = buf.reserveOffset()
        buf.writeInt(-1) // image

        val varBase = buf.index

        buf.writeSlot(rawSlot, varBase, rawBytes, buf::writeVarBytes)
        buf.writeSlot(idSlot, varBase, idBytes, buf::writeVarBytes)
        buf.writeSlot(childrenSlot, varBase, children.takeIf { it.isNotEmpty() }) { list ->
            if (list.size > DataSize.MAX_ARRAY_LENGTH) {
                throw ProtocolFormatException("Array too long in field 'Children': ${list.size}")
            }
            buf.writeVarInt(list.size)
            for (child in list) {
                writeMessage(buf, child, depth + 1)
            }
        }

        if (!hasColor) {
            buf.writeIntAt(colorSlot, -1)
        } else {
            buf.writeIntAt(colorSlot, buf.index - varBase)
            writeHexColor(buf, node.colorRgb)
        }

        buf.writeSlot(linkSlot, varBase, linkBytes, buf::writeVarBytes)
    }

    private fun writeHexColor(buf: WriteBuffer, rgb: Int) {
        buf.writeByte(HEX_COLOR_LENGTH)
        buf.writeByte('#'.code)
        val masked = rgb and 0xFFFFFF
        for (shift in 20 downTo 0 step 4) {
            buf.writeByte(HEX_DIGITS[(masked ushr shift) and 0xF])
        }
    }

    companion object {
        const val MAX_NESTING_DEPTH: Int = 32
        private const val HEADER_SIZE = 2
        private const val FORMATTED_MESSAGE_FIXED_SIZE = 40
        private const val HEX_COLOR_LENGTH = 7
        private const val HEX_COLOR_WIRE_SIZE = 1 + HEX_COLOR_LENGTH
        private val HEX_DIGITS = intArrayOf(
            '0'.code, '1'.code, '2'.code, '3'.code,
            '4'.code, '5'.code, '6'.code, '7'.code,
            '8'.code, '9'.code, 'A'.code, 'B'.code,
            'C'.code, 'D'.code, 'E'.code, 'F'.code,
        )
    }
}

internal fun String.encodeValidatedUtf8(): ByteArray {
    val bytes = toByteArray(Charsets.UTF_8)
    if (bytes.size > DataSize.MAX_STRING_LENGTH) {
        throw ProtocolFormatException("String too long: ${bytes.size} > ${DataSize.MAX_STRING_LENGTH}")
    }
    return bytes
}
