package dev.sectile.network.protocol.structures

import dev.sectile.network.api.buffer.ReadBuffer
import dev.sectile.network.api.buffer.WriteBuffer
import dev.sectile.network.protocol.ProtocolCodec
import dev.sectile.network.protocol.ProtocolFormatException
import dev.sectile.network.protocol.VarInt

/**
 * Rich text message of the Hytale protocol.
 *
 * Layout:
 *  - 2 bytes of nullability mask,
 *  - 5 bytes for the three-state booleans (bold, italic, monospace, underlined,
 *    strikethrough) and 1 byte for `markupEnabled`,
 *  - 8 offset slots of 4 bytes,
 *  - variable block with: rawText, messageId, children, params, messageParams, color,
 *    link, and image, in that order.
 *
 * Children can nest messages, so reading and writing limit the depth to
 * [MAX_NESTING_DEPTH].
 */
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

    /** Writes the message with the canonical protocol layout. */
    fun write(buf: WriteBuffer) {
        write(buf, 0)
    }

    /**
     * Serialized size of the message.
     *
     * Used as a hint for buffer allocation, so it must be exact: if it were too large,
     * the encoder would allocate too much memory.
     */
    fun computeSize(): Int {
        var size = VARIABLE_BLOCK_START
        rawText?.let { size += varStringSize(it) }
        messageId?.let { size += varStringSize(it) }
        children?.let { list ->
            size += VarInt.sizeOf(list.size)
            list.forEach { size += it.computeSize() }
        }
        params?.let { map ->
            size += VarInt.sizeOf(map.size)
            map.forEach { (key, value) ->
                size += varStringSize(key)
                size += VarInt.sizeOf(value.typeId) + value.computeSize()
            }
        }
        messageParams?.let { map ->
            size += VarInt.sizeOf(map.size)
            map.forEach { (key, value) ->
                size += varStringSize(key)
                size += value.computeSize()
            }
        }
        color?.let { size += varStringSize(it) }
        link?.let { size += varStringSize(it) }
        image?.let { size += it.computeSize() }
        return size
    }

    private fun varStringSize(value: String): Int {
        val bytes = value.toByteArray(Charsets.UTF_8).size
        return VarInt.sizeOf(bytes) + bytes
    }

    /** Reads the message with the canonical protocol layout. */
    fun read(buf: ReadBuffer) {
        read(buf, 0)
    }

    private fun write(buf: WriteBuffer, depth: Int) {
        if (depth > MAX_NESTING_DEPTH) throw ProtocolFormatException("FormattedMessage nesting too deep")
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
        buf.writeByte(if (bold == true) 1 else 0)
        buf.writeByte(if (italic == true) 1 else 0)
        buf.writeByte(if (monospace == true) 1 else 0)
        buf.writeByte(if (underlined == true) 1 else 0)
        buf.writeByte(if (strikethrough == true) 1 else 0)
        buf.writeByte(if (markupEnabled) 1 else 0)

        val rawTextSlot = ProtocolCodec.reserveOffset(buf)
        val messageIdSlot = ProtocolCodec.reserveOffset(buf)
        val childrenSlot = ProtocolCodec.reserveOffset(buf)
        val paramsSlot = ProtocolCodec.reserveOffset(buf)
        val messageParamsSlot = ProtocolCodec.reserveOffset(buf)
        val colorSlot = ProtocolCodec.reserveOffset(buf)
        val linkSlot = ProtocolCodec.reserveOffset(buf)
        val imageSlot = ProtocolCodec.reserveOffset(buf)

        val varBlockStart = buf.index

        writeString(buf, rawTextSlot, rawText, varBlockStart, MAX_STRING_LENGTH)
        writeString(buf, messageIdSlot, messageId, varBlockStart, MAX_STRING_LENGTH)

        if (children == null) {
            buf.writeIntAt(childrenSlot, -1)
        } else {
            buf.writeIntAt(childrenSlot, buf.index - varBlockStart)
            VarInt.write(buf, children!!.size)
            for (child in children!!) {
                child.write(buf, depth + 1)
            }
        }

        if (params == null) {
            buf.writeIntAt(paramsSlot, -1)
        } else {
            buf.writeIntAt(paramsSlot, buf.index - varBlockStart)
            VarInt.write(buf, params!!.size)
            for ((key, value) in params!!) {
                ProtocolCodec.writeVarString(buf, key, MAX_STRING_LENGTH)
                value.write(buf)
            }
        }

        if (messageParams == null) {
            buf.writeIntAt(messageParamsSlot, -1)
        } else {
            buf.writeIntAt(messageParamsSlot, buf.index - varBlockStart)
            VarInt.write(buf, messageParams!!.size)
            for ((key, value) in messageParams!!) {
                ProtocolCodec.writeVarString(buf, key, MAX_STRING_LENGTH)
                value.write(buf, depth + 1)
            }
        }

        writeString(buf, colorSlot, color, varBlockStart, MAX_STRING_LENGTH)
        writeString(buf, linkSlot, link, varBlockStart, MAX_STRING_LENGTH)

        if (image == null) {
            buf.writeIntAt(imageSlot, -1)
        } else {
            buf.writeIntAt(imageSlot, buf.index - varBlockStart)
            image!!.write(buf)
        }
    }

    private fun read(buf: ReadBuffer, depth: Int) {
        checkNestingDepth(depth)

        val base = buf.index
        val nullBits0 = buf.readUnsignedByte()
        val nullBits1 = buf.readUnsignedByte()
        bold = readNullableBool(buf, nullBits0, 0x01)
        italic = readNullableBool(buf, nullBits0, 0x02)
        monospace = readNullableBool(buf, nullBits0, 0x04)
        underlined = readNullableBool(buf, nullBits0, 0x08)
        strikethrough = readNullableBool(buf, nullBits0, 0x10)
        markupEnabled = buf.readBoolean()

        // The offset slots span from `base + 8` to `base + 39`.
        val varBlockStart = base + VARIABLE_BLOCK_START
        buf.skipTo(varBlockStart)

        rawText = readString(buf, base + 8, varBlockStart, nullBits0 and 0x20 != 0, "RawText")
        messageId = readString(buf, base + 12, varBlockStart, nullBits0 and 0x40 != 0, "MessageId")

        children = if (nullBits0 and 0x80 != 0) {
            ProtocolCodec.requireSlot(buf, base + 16, buf.index - varBlockStart, "Children")
            val count = VarInt.read(buf)
            if (count !in 0..MAX_STRING_LENGTH) {
                throw ProtocolFormatException("Array too long in field 'Children': $count")
            }
            val list = ArrayList<FormattedMessage>(minOf(count, 64))
            repeat(count) { list.add(FormattedMessage().also { it.read(buf, depth + 1) }) }
            list
        } else {
            ProtocolCodec.requireSlot(buf, base + 16, -1, "Children")
            null
        }

        params = if (nullBits1 and 0x01 != 0) {
            ProtocolCodec.requireSlot(buf, base + 20, buf.index - varBlockStart, "Params")
            readParams(buf)
        } else {
            ProtocolCodec.requireSlot(buf, base + 20, -1, "Params")
            null
        }

        messageParams = if (nullBits1 and 0x02 != 0) {
            ProtocolCodec.requireSlot(buf, base + 24, buf.index - varBlockStart, "MessageParams")
            readMessageParams(buf, depth)
        } else {
            ProtocolCodec.requireSlot(buf, base + 24, -1, "MessageParams")
            null
        }

        color = readString(buf, base + 28, varBlockStart, nullBits1 and 0x04 != 0, "Color")
        link = readString(buf, base + 32, varBlockStart, nullBits1 and 0x08 != 0, "Link")

        image = if (nullBits1 and 0x10 != 0) {
            ProtocolCodec.requireSlot(buf, base + 36, buf.index - varBlockStart, "Image")
            FormattedMessageImage().also { it.read(buf) }
        } else {
            ProtocolCodec.requireSlot(buf, base + 36, -1, "Image")
            null
        }
    }

    private fun readParams(buf: ReadBuffer): MutableMap<String, ParamValue> {
        val count = VarInt.read(buf)
        if (count !in 0..MAX_STRING_LENGTH) {
            throw ProtocolFormatException("Dictionary too large in field 'Params': $count")
        }
        val map = LinkedHashMap<String, ParamValue>(minOf(count, 64))
        repeat(count) {
            val key = ProtocolCodec.readVarString(buf, MAX_STRING_LENGTH, "Params")
            if (map.containsKey(key)) throw ProtocolFormatException("Duplicate key in field 'Params': $key")
            map[key] = ParamValue.read(buf)
        }
        return map
    }

    private fun readMessageParams(buf: ReadBuffer, depth: Int): MutableMap<String, FormattedMessage> {
        val count = VarInt.read(buf)
        if (count !in 0..MAX_STRING_LENGTH) {
            throw ProtocolFormatException("Dictionary too large in field 'MessageParams': $count")
        }
        val map = LinkedHashMap<String, FormattedMessage>(minOf(count, 64))
        repeat(count) {
            val key = ProtocolCodec.readVarString(buf, MAX_STRING_LENGTH, "MessageParams")
            if (map.containsKey(key)) {
                throw ProtocolFormatException("Duplicate key in field 'MessageParams': $key")
            }
            map[key] = FormattedMessage().also { it.read(buf, depth + 1) }
        }
        return map
    }

    private fun readNullableBool(buf: ReadBuffer, nullBits: Int, mask: Int): Boolean? {
        val present = nullBits and mask != 0
        val value = buf.readBoolean()
        return if (present) value else null
    }

    private fun readString(
        buf: ReadBuffer,
        slot: Int,
        varBlockStart: Int,
        present: Boolean,
        field: String,
    ): String? {
        if (!present) {
            ProtocolCodec.requireSlot(buf, slot, -1, field)
            return null
        }
        ProtocolCodec.requireSlot(buf, slot, buf.index - varBlockStart, field)
        return ProtocolCodec.readVarString(buf, MAX_STRING_LENGTH, field)
    }

    private fun writeString(
        buf: WriteBuffer,
        slot: Int,
        value: String?,
        varBlockStart: Int,
        maxLength: Int,
    ) {
        if (value == null) {
            buf.writeIntAt(slot, -1)
            return
        }
        buf.writeIntAt(slot, buf.index - varBlockStart)
        ProtocolCodec.writeVarString(buf, value, maxLength)
    }

    companion object {
        const val MAX_STRING_LENGTH: Int = 4_096_000
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
