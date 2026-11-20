package dev.sectile.network.protocol.packet.server

import dev.sectile.network.api.buffer.WriteBuffer
import dev.sectile.network.api.packet.OutboundPacket
import dev.sectile.network.protocol.ProtocolCodec
import dev.sectile.network.protocol.ProtocolFormatException
import dev.sectile.network.protocol.ProtocolPacketTable
import dev.sectile.network.protocol.VarInt

interface FormattedMessageData {
    val rawText: String?
    val messageId: String?
    val colorRgb: Int
    val bold: Boolean
    val italic: Boolean
    val monospace: Boolean
    val underline: Boolean
    val strikethrough: Boolean
    val markupEnabled: Boolean
        get() = false
    val link: String?
    val children: List<FormattedMessageData>
}

class ServerMessagePacket(
    var message: FormattedMessageData? = null,
    var chatType: Byte = 0,
) : OutboundPacket {

    override val definition = ProtocolPacketTable.SERVER_MESSAGE

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
        node.rawText?.let { total += varBytesSize(encodeValidated(it).size) }
        node.messageId?.let { total += varBytesSize(encodeValidated(it).size) }
        val children = node.children
        if (children.isNotEmpty()) {
            if (children.size > ProtocolCodec.MAX_STRING_LENGTH) {
                throw ProtocolFormatException("Array too long in field 'Children': ${children.size}")
            }
            total += VarInt.sizeOf(children.size)
            for (child in children) {
                total += computeMessageSize(child, depth + 1)
            }
        }
        if (node.colorRgb >= 0) {
            total += HEX_COLOR_WIRE_SIZE
        }
        node.link?.let { total += varBytesSize(encodeValidated(it).size) }
        return total
    }

    private fun writeMessage(buf: WriteBuffer, node: FormattedMessageData, depth: Int) {
        if (depth > MAX_NESTING_DEPTH) {
            throw ProtocolFormatException("FormattedMessage depth exceeded $MAX_NESTING_DEPTH")
        }

        val rawBytes = node.rawText?.let(::encodeValidated)
        val idBytes = node.messageId?.let(::encodeValidated)
        val children = node.children
        val hasChildren = children.isNotEmpty()
        val hasColor = node.colorRgb >= 0
        val linkBytes = node.link?.let(::encodeValidated)

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

        val rawSlot = ProtocolCodec.reserveOffset(buf)
        val idSlot = ProtocolCodec.reserveOffset(buf)
        val childrenSlot = ProtocolCodec.reserveOffset(buf)
        buf.writeInt(-1) // params
        buf.writeInt(-1) // messageParams
        val colorSlot = ProtocolCodec.reserveOffset(buf)
        val linkSlot = ProtocolCodec.reserveOffset(buf)
        buf.writeInt(-1) // image

        val varBase = buf.index

        if (rawBytes == null) {
            buf.writeIntAt(rawSlot, -1)
        } else {
            buf.writeIntAt(rawSlot, buf.index - varBase)
            writeVarBytes(buf, rawBytes)
        }

        if (idBytes == null) {
            buf.writeIntAt(idSlot, -1)
        } else {
            buf.writeIntAt(idSlot, buf.index - varBase)
            writeVarBytes(buf, idBytes)
        }

        if (!hasChildren) {
            buf.writeIntAt(childrenSlot, -1)
        } else {
            if (children.size > ProtocolCodec.MAX_STRING_LENGTH) {
                throw ProtocolFormatException("Array too long in field 'Children': ${children.size}")
            }
            buf.writeIntAt(childrenSlot, buf.index - varBase)
            VarInt.write(buf, children.size)
            for (child in children) {
                writeMessage(buf, child, depth + 1)
            }
        }

        if (!hasColor) {
            buf.writeIntAt(colorSlot, -1)
        } else {
            buf.writeIntAt(colorSlot, buf.index - varBase)
            writeHexColor(buf, node.colorRgb)
        }

        if (linkBytes == null) {
            buf.writeIntAt(linkSlot, -1)
        } else {
            buf.writeIntAt(linkSlot, buf.index - varBase)
            writeVarBytes(buf, linkBytes)
        }
    }

    private fun writeHexColor(buf: WriteBuffer, rgb: Int) {
        buf.writeByte(HEX_COLOR_LENGTH)
        buf.writeByte('#'.code)
        val masked = rgb and 0xFFFFFF
        for (shift in 20 downTo 0 step 4) {
            buf.writeByte(HEX_DIGITS[(masked ushr shift) and 0xF])
        }
    }

    private fun writeVarBytes(buf: WriteBuffer, bytes: ByteArray) {
        VarInt.write(buf, bytes.size)
        buf.writeBytes(bytes)
    }

    private fun varBytesSize(length: Int): Int = VarInt.sizeOf(length) + length

    private fun encodeValidated(value: String): ByteArray {
        val bytes = value.toByteArray(Charsets.UTF_8)
        if (bytes.size > ProtocolCodec.MAX_STRING_LENGTH) {
            throw ProtocolFormatException("String too long: ${bytes.size} > ${ProtocolCodec.MAX_STRING_LENGTH}")
        }
        return bytes
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
