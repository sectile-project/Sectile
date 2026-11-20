package dev.sectile.network.protocol.structures

import dev.sectile.network.api.buffer.ReadBuffer
import dev.sectile.network.api.buffer.WriteBuffer
import dev.sectile.network.protocol.ProtocolCodec
import dev.sectile.network.protocol.ProtocolFormatException
import dev.sectile.network.protocol.VarInt

/**
 * Parameter value of a [FormattedMessage].
 *
 * It is polymorphic: a varint with the type id followed by the subtype payload.
 */
sealed class ParamValue {

    /** Type id that travels over the wire. */
    abstract val typeId: Int

    /** Writes the subtype payload (without the type id). */
    abstract fun writePayload(buf: WriteBuffer)

    /** Reads the subtype payload (without the type id). */
    abstract fun readPayload(buf: ReadBuffer)

    /** Writes the complete value (type id + payload). */
    fun write(buf: WriteBuffer) {
        VarInt.write(buf, typeId)
        writePayload(buf)
    }

    /** Size of the subtype payload (without the type id). */
    abstract fun computeSize(): Int

    /** Size of the complete value (type id + payload). */
    fun computeSizeWithTypeId(): Int = VarInt.sizeOf(typeId) + computeSize()

    companion object {
        /** Reads a complete value. */
        fun read(buf: ReadBuffer): ParamValue {
            val typeId = VarInt.read(buf)
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

/** String-type parameter. */
class StringParamValue(var value: String? = null) : ParamValue() {

    override val typeId: Int get() = TYPE_STRING

    override fun writePayload(buf: WriteBuffer) {
        val current = value
        if (current == null) {
            buf.writeByte(0)
            return
        }
        buf.writeByte(1)
        ProtocolCodec.writeVarString(buf, current, MAX_LENGTH)
    }

    override fun readPayload(buf: ReadBuffer) {
        val nullBits = buf.readUnsignedByte()
        value = if (nullBits and 0x01 != 0) ProtocolCodec.readVarString(buf, MAX_LENGTH, "Value") else null
    }

    override fun computeSize(): Int {
        val current = value ?: return 1
        val bytes = current.toByteArray(Charsets.UTF_8).size
        return 1 + VarInt.sizeOf(bytes) + bytes
    }

    companion object {
        const val MAX_LENGTH: Int = 4_096_000
    }
}

/** Boolean-type parameter. */
class BoolParamValue(var value: Boolean = false) : ParamValue() {

    override val typeId: Int get() = TYPE_BOOL

    override fun writePayload(buf: WriteBuffer) {
        buf.writeBoolean(value)
    }

    override fun readPayload(buf: ReadBuffer) {
        value = buf.readBoolean()
    }

    override fun computeSize(): Int = 1
}

/** Double-type parameter. */
class DoubleParamValue(var value: Double = 0.0) : ParamValue() {

    override val typeId: Int get() = TYPE_DOUBLE

    override fun writePayload(buf: WriteBuffer) {
        buf.writeDouble(value)
    }

    override fun readPayload(buf: ReadBuffer) {
        value = buf.readDouble()
    }

    override fun computeSize(): Int = 8
}

/** Int-type parameter. */
class IntParamValue(var value: Int = 0) : ParamValue() {

    override val typeId: Int get() = TYPE_INT

    override fun writePayload(buf: WriteBuffer) {
        buf.writeInt(value)
    }

    override fun readPayload(buf: ReadBuffer) {
        value = buf.readInt()
    }

    override fun computeSize(): Int = 4
}

/** Long-type parameter. */
class LongParamValue(var value: Long = 0L) : ParamValue() {

    override val typeId: Int get() = TYPE_LONG

    override fun writePayload(buf: WriteBuffer) {
        buf.writeLong(value)
    }

    override fun readPayload(buf: ReadBuffer) {
        value = buf.readLong()
    }

    override fun computeSize(): Int = 8
}
