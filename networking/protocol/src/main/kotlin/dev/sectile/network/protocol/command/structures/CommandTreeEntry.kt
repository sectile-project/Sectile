package dev.sectile.network.protocol.command.structures

import dev.sectile.network.api.buffer.WriteBuffer
import dev.sectile.network.api.buffer.writeSlot
import dev.sectile.network.protocol.DataSize
import dev.sectile.network.protocol.ProtocolFormatException

class CommandTreeEntry(
    var name: String? = null,
    var aliases: List<String>? = null,
    var usageText: String? = null,
    var description: String? = null,
    var subcommands: List<CommandTreeEntry>? = null,
    var subcommandHints: List<String>? = null,
    var requiredArgs: List<CommandArgInfo>? = null,
    var optionalArgs: List<CommandOptionalArgEntry>? = null,
) {

    fun write(buf: WriteBuffer) {
        write(buf, 0)
    }

    fun computeSize(): Int {
        var total = VARIABLE_BLOCK_START
        name?.let { total += DataSize.varStringSize(it) }
        aliases?.let { total += DataSize.stringListSize(it) }
        usageText?.let { total += DataSize.varStringSize(it) }
        description?.let { total += DataSize.varStringSize(it) }
        subcommands?.let { total += DataSize.varListSize(it, CommandTreeEntry::computeSize) }
        subcommandHints?.let { total += DataSize.stringListSize(it) }
        requiredArgs?.let { total += DataSize.varListSize(it, CommandArgInfo::computeSize) }
        optionalArgs?.let { total += DataSize.varListSize(it, CommandOptionalArgEntry::computeSize) }
        return total
    }

    private fun write(buf: WriteBuffer, depth: Int) {
        if (depth > MAX_NESTING_DEPTH) {
            throw ProtocolFormatException("CommandTreeEntry nesting depth exceeded $MAX_NESTING_DEPTH")
        }

        var nullBits0 = 0
        if (name != null) nullBits0 = nullBits0 or 0x01
        if (aliases != null) nullBits0 = nullBits0 or 0x02
        if (usageText != null) nullBits0 = nullBits0 or 0x04
        if (description != null) nullBits0 = nullBits0 or 0x08
        if (subcommands != null) nullBits0 = nullBits0 or 0x20
        if (subcommandHints != null) nullBits0 = nullBits0 or 0x40
        if (requiredArgs != null) nullBits0 = nullBits0 or 0x80

        buf.writeByte(nullBits0)
        buf.writeByte(if (optionalArgs != null) 0x01 else 0x00)

        val nameSlot = buf.reserveOffset()
        val aliasesSlot = buf.reserveOffset()
        val usageSlot = buf.reserveOffset()
        val descSlot = buf.reserveOffset()
        val variantsSlot = buf.reserveOffset()
        val subcommandsSlot = buf.reserveOffset()
        val subHintsSlot = buf.reserveOffset()
        val requiredSlot = buf.reserveOffset()
        val optionalSlot = buf.reserveOffset()
        val overridesSlot = buf.reserveOffset()

        val varBlockStart = buf.index

        buf.writeStringSlot(nameSlot, varBlockStart, name)
        buf.writeStringListSlot(aliasesSlot, varBlockStart, aliases, "Aliases")
        buf.writeStringSlot(usageSlot, varBlockStart, usageText)
        buf.writeStringSlot(descSlot, varBlockStart, description)
        buf.writeIntAt(variantsSlot, -1)
        buf.writeListSlot(subcommandsSlot, varBlockStart, subcommands, "Subcommands") {
            it.write(buf, depth + 1)
        }
        buf.writeStringListSlot(subHintsSlot, varBlockStart, subcommandHints, "SubcommandHints")
        buf.writeListSlot(requiredSlot, varBlockStart, requiredArgs, "RequiredArgs") { it.write(buf) }
        buf.writeListSlot(optionalSlot, varBlockStart, optionalArgs, "OptionalArgs") { it.write(buf) }
        buf.writeIntAt(overridesSlot, -1)
    }

    companion object {
        const val VARIABLE_BLOCK_START: Int = 42
        const val MAX_NESTING_DEPTH: Int = 32
    }
}

internal fun checkCommandArrayBounds(size: Int, field: String) {
    if (size !in 0..DataSize.MAX_ARRAY_LENGTH) {
        throw ProtocolFormatException("Array too long in field '$field': $size")
    }
}

internal fun WriteBuffer.writeStringSlot(slot: Int, varBlockStart: Int, value: String?) {
    writeSlot(slot, varBlockStart, value) { writeVarString(it, DataSize.MAX_STRING_LENGTH) }
}

internal fun WriteBuffer.writeStringListSlot(
    slot: Int,
    varBlockStart: Int,
    values: List<String>?,
    field: String,
) {
    writeListSlot(slot, varBlockStart, values, field) { writeVarString(it, DataSize.MAX_STRING_LENGTH) }
}

internal inline fun <T> WriteBuffer.writeListSlot(
    slot: Int,
    varBlockStart: Int,
    values: List<T>?,
    field: String,
    writeItem: (T) -> Unit,
) {
    writeSlot(slot, varBlockStart, values) { list ->
        checkCommandArrayBounds(list.size, field)
        writeVarInt(list.size)
        list.forEach(writeItem)
    }
}
