package dev.sectile.network.protocol.shared.text

import dev.sectile.network.api.buffer.WriteBuffer
import dev.sectile.network.api.buffer.writeSlot
import dev.sectile.network.protocol.DataSize
import dev.sectile.network.protocol.ProtocolFormatException

class FormattedMessage {

    var bold: Boolean? = null
    var italic: Boolean? = null
    var monospace: Boolean? = null
    var underlined: Boolean? = null
    var strikethrough: Boolean? = null
    var markupEnabled: Boolean = false

    var rawText: String? = null
    var messageId: String? = null
    var children: MutableList<FormattedMessage>? = null
    var params: MutableMap<String, ParamValue>? = null
    var messageParams: MutableMap<String, FormattedMessage>? = null
    var color: String? = null
    var link: String? = null
    var image: FormattedMessageImage? = null

    fun write(buf: WriteBuffer) {
        write(buf, 0)
    }

    fun computeSize(): Int {
        var size = VARIABLE_BLOCK_START
        rawText?.let { size += DataSize.varStringSize(it) }
        messageId?.let { size += DataSize.varStringSize(it) }
        children?.let { list ->
            size += DataSize.varListSize(list, FormattedMessage::computeSize)
        }
        params?.let { map ->
            size += DataSize.varInt(map.size)
            map.forEach { (key, value) ->
                size += DataSize.varStringSize(key) + value.computeSizeWithTypeId()
            }
        }
        messageParams?.let { map ->
            size += DataSize.varInt(map.size)
            map.forEach { (key, value) ->
                size += DataSize.varStringSize(key) + value.computeSize()
            }
        }
        color?.let { size += DataSize.varStringSize(it) }
        link?.let { size += DataSize.varStringSize(it) }
        image?.let { size += it.computeSize() }
        return size
    }

    private fun write(buf: WriteBuffer, depth: Int) {
        checkNestingDepth(depth)

        var nullBits0 = 0
        var nullBits1 = 0
        if (bold != null) nullBits0 = nullBits0 or 0x01
        if (italic != null) nullBits0 = nullBits0 or 0x02
        if (monospace != null) nullBits0 = nullBits0 or 0x04
        if (underlined != null) nullBits0 = nullBits0 or 0x08
        if (strikethrough != null) nullBits0 = nullBits0 or 0x10
        if (rawText != null) nullBits0 = nullBits0 or 0x20
        if (messageId != null) nullBits0 = nullBits0 or 0x40
        if (children != null) nullBits0 = nullBits0 or 0x80
        if (params != null) nullBits1 = nullBits1 or 0x01
        if (messageParams != null) nullBits1 = nullBits1 or 0x02
        if (color != null) nullBits1 = nullBits1 or 0x04
        if (link != null) nullBits1 = nullBits1 or 0x08
        if (image != null) nullBits1 = nullBits1 or 0x10

        buf.writeByte(nullBits0)
        buf.writeByte(nullBits1)
        buf.writeBoolean(bold == true)
        buf.writeBoolean(italic == true)
        buf.writeBoolean(monospace == true)
        buf.writeBoolean(underlined == true)
        buf.writeBoolean(strikethrough == true)
        buf.writeBoolean(markupEnabled)

        val rawTextSlot = buf.reserveOffset()
        val messageIdSlot = buf.reserveOffset()
        val childrenSlot = buf.reserveOffset()
        val paramsSlot = buf.reserveOffset()
        val messageParamsSlot = buf.reserveOffset()
        val colorSlot = buf.reserveOffset()
        val linkSlot = buf.reserveOffset()
        val imageSlot = buf.reserveOffset()

        val varBlockStart = buf.index

        buf.writeSlot(rawTextSlot, varBlockStart, rawText) { buf.writeVarString(it, MAX_STRING_LENGTH) }
        buf.writeSlot(messageIdSlot, varBlockStart, messageId) { buf.writeVarString(it, MAX_STRING_LENGTH) }
        buf.writeSlot(childrenSlot, varBlockStart, children) { list ->
            buf.writeVarInt(list.size)
            for (child in list) {
                child.write(buf, depth + 1)
            }
        }
        buf.writeSlot(paramsSlot, varBlockStart, params) { map ->
            buf.writeVarInt(map.size)
            for ((key, value) in map) {
                buf.writeVarString(key, MAX_STRING_LENGTH)
                value.write(buf)
            }
        }
        buf.writeSlot(messageParamsSlot, varBlockStart, messageParams) { map ->
            buf.writeVarInt(map.size)
            for ((key, value) in map) {
                buf.writeVarString(key, MAX_STRING_LENGTH)
                value.write(buf, depth + 1)
            }
        }
        buf.writeSlot(colorSlot, varBlockStart, color) { buf.writeVarString(it, MAX_STRING_LENGTH) }
        buf.writeSlot(linkSlot, varBlockStart, link) { buf.writeVarString(it, MAX_STRING_LENGTH) }
        buf.writeSlot(imageSlot, varBlockStart, image) { it.write(buf) }
    }

    companion object {
        const val MAX_STRING_LENGTH: Int = DataSize.MAX_STRING_LENGTH
        const val MAX_NESTING_DEPTH: Int = 32
        const val MAX_SIZE: Int = 1_677_721_600
        const val VARIABLE_BLOCK_START: Int = 40

        private fun checkNestingDepth(depth: Int) {
            if (depth > MAX_NESTING_DEPTH) {
                throw ProtocolFormatException("FormattedMessage nesting too deep (max $MAX_NESTING_DEPTH)")
            }
        }
    }
}
