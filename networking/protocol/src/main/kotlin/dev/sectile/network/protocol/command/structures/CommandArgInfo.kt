package dev.sectile.network.protocol.command.structures

import dev.sectile.network.api.buffer.WriteBuffer
import dev.sectile.network.protocol.DataSize

class CommandArgInfo(
    var name: String? = null,
    var argTypeId: String? = null,
    var argTypeName: String? = null,
    var valueCount: Int = -1,
    var description: String? = null,
) {

    fun write(buf: WriteBuffer) {
        var nullBits = 0
        if (name != null) nullBits = nullBits or 0x01
        if (argTypeId != null) nullBits = nullBits or 0x02
        if (argTypeName != null) nullBits = nullBits or 0x04
        if (description != null) nullBits = nullBits or 0x08

        buf.writeByte(nullBits)
        buf.writeInt(valueCount)

        val nameSlot = buf.reserveOffset()
        val typeIdSlot = buf.reserveOffset()
        val typeNameSlot = buf.reserveOffset()
        val descSlot = buf.reserveOffset()

        val varBlockStart = buf.index

        buf.writeStringSlot(nameSlot, varBlockStart, name)
        buf.writeStringSlot(typeIdSlot, varBlockStart, argTypeId)
        buf.writeStringSlot(typeNameSlot, varBlockStart, argTypeName)
        buf.writeStringSlot(descSlot, varBlockStart, description)
    }

    fun computeSize(): Int {
        var total = VARIABLE_BLOCK_START
        name?.let { total += DataSize.varStringSize(it) }
        argTypeId?.let { total += DataSize.varStringSize(it) }
        argTypeName?.let { total += DataSize.varStringSize(it) }
        description?.let { total += DataSize.varStringSize(it) }
        return total
    }

    companion object {
        const val VARIABLE_BLOCK_START: Int = 21
    }
}
