package dev.sectile.network.protocol.interfaceui

import dev.sectile.network.api.buffer.WriteBuffer
import dev.sectile.network.api.packet.OutboundPacket
import dev.sectile.network.api.packet.PacketDefinition
import dev.sectile.network.protocol.ProtocolCodec
import dev.sectile.network.protocol.ProtocolFormatException
import dev.sectile.network.protocol.ProtocolPacketTable
import dev.sectile.network.protocol.VarInt

class ArgValuesResponsePacket(
    var argTypeId: String? = null,
    var values: List<String>? = null,
    var continuations: List<Boolean>? = null,
    var isComplete: Boolean = true,
) : OutboundPacket {

    override val definition: PacketDefinition
        get() = ProtocolPacketTable.ARG_VALUES_RESPONSE

    override fun write(buf: WriteBuffer) {
        var nullBits = 0
        if (argTypeId != null) nullBits = nullBits or 0x01
        if (values != null) nullBits = nullBits or 0x02
        if (continuations != null) nullBits = nullBits or 0x04

        buf.writeByte(nullBits)
        buf.writeBoolean(isComplete)

        val argTypeIdSlot = ProtocolCodec.reserveOffset(buf)
        val valuesSlot = ProtocolCodec.reserveOffset(buf)
        val continuationsSlot = ProtocolCodec.reserveOffset(buf)
        val varBlockStart = buf.index

        if (argTypeId == null) {
            buf.writeIntAt(argTypeIdSlot, -1)
        } else {
            buf.writeIntAt(argTypeIdSlot, buf.index - varBlockStart)
            ProtocolCodec.writeVarString(buf, argTypeId!!, ProtocolCodec.MAX_STRING_LENGTH)
        }

        if (values == null) {
            buf.writeIntAt(valuesSlot, -1)
        } else {
            val list = values!!
            if (list.size > ProtocolCodec.MAX_STRING_LENGTH) {
                throw ProtocolFormatException("Array too long in field 'Values': ${list.size}")
            }
            buf.writeIntAt(valuesSlot, buf.index - varBlockStart)
            VarInt.write(buf, list.size)
            list.forEach { ProtocolCodec.writeVarString(buf, it, ProtocolCodec.MAX_STRING_LENGTH) }
        }

        if (continuations == null) {
            buf.writeIntAt(continuationsSlot, -1)
        } else {
            val list = continuations!!
            if (list.size > ProtocolCodec.MAX_STRING_LENGTH) {
                throw ProtocolFormatException("Array too long in field 'Continuations': ${list.size}")
            }
            buf.writeIntAt(continuationsSlot, buf.index - varBlockStart)
            VarInt.write(buf, list.size)
            list.forEach { buf.writeBoolean(it) }
        }
    }

    override fun size(): Int {
        var total = VARIABLE_BLOCK_START
        argTypeId?.let {
            val bytes = it.toByteArray(Charsets.UTF_8).size
            total += VarInt.sizeOf(bytes) + bytes
        }
        values?.let { list ->
            total += VarInt.sizeOf(list.size)
            list.forEach {
                val bytes = it.toByteArray(Charsets.UTF_8).size
                total += VarInt.sizeOf(bytes) + bytes
            }
        }
        continuations?.let { list ->
            total += VarInt.sizeOf(list.size) + list.size
        }
        return total
    }

    companion object {
        const val VARIABLE_BLOCK_START: Int = 14
    }
}
