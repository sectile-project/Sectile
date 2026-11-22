package dev.sectile.network.protocol.shared.text

import dev.sectile.network.api.buffer.ReadBuffer
import dev.sectile.network.api.buffer.WriteBuffer
import dev.sectile.network.protocol.DataSize
import dev.sectile.network.protocol.ProtocolFormatException

sealed class ParamValue {

    abstract val typeId: Int

    abstract fun writePayload(buf: WriteBuffer)

    abstract fun readPayload(buf: ReadBuffer)

    fun write(buf: WriteBuffer) {
        buf.writeVarInt(typeId)
        writePayload(buf)
    }

    abstract fun computeSize(): Int

    fun computeSizeWithTypeId(): Int = DataSize.varInt(typeId) + computeSize()

    companion object {
        fun read(buf: ReadBuffer): ParamValue {
            val typeId = buf.readVarInt()
            return when (typeId) {
                TYPE_STRING -> StringParamValue().apply { readPayload(buf) }
                TYPE_BOOL -> BoolParamValue().apply { readPayload(buf) }
                TYPE_DOUBLE -> DoubleParamValue().apply { readPayload(buf) }
                TYPE_INT -> IntParamValue().apply { readPayload(buf) }
                TYPE_LONG -> LongParamValue().apply { readPayload(buf) }
                else -> throw ProtocolFormatException("Unknown polymorphic type 'ParamValue': $typeId")
            }
        }

        const val TYPE_STRING: Int = 0
        const val TYPE_BOOL: Int = 1
        const val TYPE_DOUBLE: Int = 2
        const val TYPE_INT: Int = 3
        const val TYPE_LONG: Int = 4
    }
}

class StringParamValue(var value: String? = null) : ParamValue() {

    override val typeId: Int get() = TYPE_STRING

    override fun writePayload(buf: WriteBuffer) {
        val current = value
        if (current == null) {
            buf.writeByte(0)
            return
        }
        buf.writeByte(1)
        buf.writeVarString(current, MAX_LENGTH)
    }

    override fun readPayload(buf: ReadBuffer) {
        val nullBits = buf.readUnsignedByte()
        value = if (nullBits and 0x01 != 0) buf.readVarString(MAX_LENGTH, "Value") else null
    }

    override fun computeSize(): Int {
        val current = value ?: return DataSize.BYTE
        return DataSize.BYTE + DataSize.varStringSize(current)
    }

    companion object {
        const val MAX_LENGTH: Int = DataSize.MAX_STRING_LENGTH
    }
}

class BoolParamValue(var value: Boolean = false) : ParamValue() {

    override val typeId: Int get() = TYPE_BOOL

    override fun writePayload(buf: WriteBuffer) {
        buf.writeBoolean(value)
    }

    override fun readPayload(buf: ReadBuffer) {
        value = buf.readBoolean()
    }

    override fun computeSize(): Int = DataSize.BOOLEAN
}

class DoubleParamValue(var value: Double = 0.0) : ParamValue() {

    override val typeId: Int get() = TYPE_DOUBLE

    override fun writePayload(buf: WriteBuffer) {
        buf.writeDouble(value)
    }

    override fun readPayload(buf: ReadBuffer) {
        value = buf.readDouble()
    }

    override fun computeSize(): Int = DataSize.DOUBLE
}

class IntParamValue(var value: Int = 0) : ParamValue() {

    override val typeId: Int get() = TYPE_INT

    override fun writePayload(buf: WriteBuffer) {
        buf.writeInt(value)
    }

    override fun readPayload(buf: ReadBuffer) {
        value = buf.readInt()
    }

    override fun computeSize(): Int = DataSize.INT
}

class LongParamValue(var value: Long = 0L) : ParamValue() {

    override val typeId: Int get() = TYPE_LONG

    override fun writePayload(buf: WriteBuffer) {
        buf.writeLong(value)
    }

    override fun readPayload(buf: ReadBuffer) {
        value = buf.readLong()
    }

    override fun computeSize(): Int = DataSize.LONG
}
