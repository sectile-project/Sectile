package dev.sectile.network.protocol.command.structures

import dev.sectile.network.api.buffer.WriteBuffer
import dev.sectile.network.protocol.DataSize

class CommandOptionalArgEntry(
    var name: String? = null,
    var hint: String? = null,
    var argTypeId: String? = null,
    var description: String? = null,
) {

    fun write(buf: WriteBuffer) {
        var nullBits = 0
        if (name != null) nullBits = nullBits or 0x01
        if (hint != null) nullBits = nullBits or 0x02
        if (argTypeId != null) nullBits = nullBits or 0x04
        if (description != null) nullBits = nullBits or 0x08

        buf.writeByte(nullBits)

        val nameSlot = buf.reserveOffset()
        val hintSlot = buf.reserveOffset()
        val typeIdSlot = buf.reserveOffset()
        val descSlot = buf.reserveOffset()

        val varBlockStart = buf.index

        buf.writeStringSlot(nameSlot, varBlockStart, name)
        buf.writeStringSlot(hintSlot, varBlockStart, hint)
        buf.writeStringSlot(typeIdSlot, varBlockStart, argTypeId)
        buf.writeStringSlot(descSlot, varBlockStart, description)
    }

    fun computeSize(): Int {
        var total = VARIABLE_BLOCK_START
        name?.let { total += DataSize.varStringSize(it) }
        hint?.let { total += DataSize.varStringSize(it) }
        argTypeId?.let { total += DataSize.varStringSize(it) }
        description?.let { total += DataSize.varStringSize(it) }
        return total
    }

    companion object {
        const val VARIABLE_BLOCK_START: Int = 17
    }
}
