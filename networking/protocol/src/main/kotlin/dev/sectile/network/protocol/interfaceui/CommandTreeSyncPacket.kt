package dev.sectile.network.protocol.interfaceui

import dev.sectile.network.api.buffer.WriteBuffer
import dev.sectile.network.api.packet.OutboundPacket
import dev.sectile.network.api.packet.PacketDefinition
import dev.sectile.network.protocol.ProtocolCodec
import dev.sectile.network.protocol.ProtocolFormatException
import dev.sectile.network.protocol.ProtocolPacketTable
import dev.sectile.network.protocol.VarInt
import dev.sectile.network.protocol.data.DataSize
import dev.sectile.network.protocol.data.DataSize.stringListSize
import dev.sectile.network.protocol.data.DataSize.varStringSize

class CommandTreeSyncPacket(
    var commands: List<CommandTreeEntry>? = null,
) : OutboundPacket {

    override val definition: PacketDefinition
        get() = ProtocolPacketTable.COMMAND_TREE_SYNC

    override fun write(buf: WriteBuffer) {
        val current = commands
        if (current == null) {
            buf.writeByte(0)
            return
        }
        buf.writeByte(0x01)
        checkArrayBounds(current.size, "Commands")
        VarInt.write(buf, current.size)
        current.forEach { it.write(buf) }
    }

    override fun size(): Int {
        var total = VARIABLE_BLOCK_START
        commands?.let { list ->
            total += VarInt.sizeOf(list.size)
            list.forEach { total += it.computeSize() }
        }
        return total
    }

    companion object {
        const val VARIABLE_BLOCK_START: Int = 1
        const val MAX_NESTING_DEPTH: Int = 32
    }
}

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
        name?.let { total += varStringSize(it) }
        aliases?.let { total += stringListSize(it) }
        usageText?.let { total += varStringSize(it) }
        description?.let { total += varStringSize(it) }
        subcommands?.let { list ->
            total += VarInt.sizeOf(list.size)
            list.forEach { total += it.computeSize() }
        }
        subcommandHints?.let { total += stringListSize(it) }
        requiredArgs?.let { list ->
            total += VarInt.sizeOf(list.size)
            list.forEach { total += it.computeSize() }
        }
        optionalArgs?.let { list ->
            total += VarInt.sizeOf(list.size)
            list.forEach { total += it.computeSize() }
        }
        return total
    }

    private fun write(buf: WriteBuffer, depth: Int) {
        if (depth > CommandTreeSyncPacket.MAX_NESTING_DEPTH) {
            throw ProtocolFormatException("CommandTreeEntry nesting depth exceeded ${CommandTreeSyncPacket.MAX_NESTING_DEPTH}")
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

        val nameSlot = ProtocolCodec.reserveOffset(buf)
        val aliasesSlot = ProtocolCodec.reserveOffset(buf)
        val usageSlot = ProtocolCodec.reserveOffset(buf)
        val descSlot = ProtocolCodec.reserveOffset(buf)
        val variantsSlot = ProtocolCodec.reserveOffset(buf)
        val subcommandsSlot = ProtocolCodec.reserveOffset(buf)
        val subHintsSlot = ProtocolCodec.reserveOffset(buf)
        val requiredSlot = ProtocolCodec.reserveOffset(buf)
        val optionalSlot = ProtocolCodec.reserveOffset(buf)
        val overridesSlot = ProtocolCodec.reserveOffset(buf)

        val varBlockStart = buf.index

        writeOptionalString(buf, nameSlot, name, varBlockStart)
        writeOptionalStringList(buf, aliasesSlot, aliases, varBlockStart, "Aliases")
        writeOptionalString(buf, usageSlot, usageText, varBlockStart)
        writeOptionalString(buf, descSlot, description, varBlockStart)
        buf.writeIntAt(variantsSlot, -1)
        writeOptionalList(buf, subcommandsSlot, subcommands, varBlockStart, "Subcommands") {
            it.write(buf, depth + 1)
        }
        writeOptionalStringList(buf, subHintsSlot, subcommandHints, varBlockStart, "SubcommandHints")
        writeOptionalList(buf, requiredSlot, requiredArgs, varBlockStart, "RequiredArgs") { it.write(buf) }
        writeOptionalList(buf, optionalSlot, optionalArgs, varBlockStart, "OptionalArgs") { it.write(buf) }
        buf.writeIntAt(overridesSlot, -1)
    }

    companion object {
        const val VARIABLE_BLOCK_START: Int = 42
    }
}

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

        val nameSlot = ProtocolCodec.reserveOffset(buf)
        val typeIdSlot = ProtocolCodec.reserveOffset(buf)
        val typeNameSlot = ProtocolCodec.reserveOffset(buf)
        val descSlot = ProtocolCodec.reserveOffset(buf)

        val varBlockStart = buf.index

        writeOptionalString(buf, nameSlot, name, varBlockStart)
        writeOptionalString(buf, typeIdSlot, argTypeId, varBlockStart)
        writeOptionalString(buf, typeNameSlot, argTypeName, varBlockStart)
        writeOptionalString(buf, descSlot, description, varBlockStart)
    }

    fun computeSize(): Int {
        var total = VARIABLE_BLOCK_START
        name?.let { total += varStringSize(it) }
        argTypeId?.let { total += varStringSize(it) }
        argTypeName?.let { total += varStringSize(it) }
        description?.let { total += varStringSize(it) }
        return total
    }

    companion object {
        const val VARIABLE_BLOCK_START: Int = 21
    }
}

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

        val nameSlot = ProtocolCodec.reserveOffset(buf)
        val hintSlot = ProtocolCodec.reserveOffset(buf)
        val typeIdSlot = ProtocolCodec.reserveOffset(buf)
        val descSlot = ProtocolCodec.reserveOffset(buf)

        val varBlockStart = buf.index

        writeOptionalString(buf, nameSlot, name, varBlockStart)
        writeOptionalString(buf, hintSlot, hint, varBlockStart)
        writeOptionalString(buf, typeIdSlot, argTypeId, varBlockStart)
        writeOptionalString(buf, descSlot, description, varBlockStart)
    }

    fun computeSize(): Int {
        var total = VARIABLE_BLOCK_START
        name?.let { total += varStringSize(it) }
        hint?.let { total += varStringSize(it) }
        argTypeId?.let { total += varStringSize(it) }
        description?.let { total += varStringSize(it) }
        return total
    }

    companion object {
        const val VARIABLE_BLOCK_START: Int = 17
    }
}

private fun checkArrayBounds(size: Int, field: String) {
    if (size !in 0..ProtocolCodec.MAX_STRING_LENGTH) {
        throw ProtocolFormatException("Array too long in field '$field': $size")
    }
}

private fun writeOptionalString(
    buf: WriteBuffer,
    slot: Int,
    value: String?,
    varBlockStart: Int,
) {
    if (value == null) {
        buf.writeIntAt(slot, -1)
        return
    }
    buf.writeIntAt(slot, buf.index - varBlockStart)
    ProtocolCodec.writeVarString(buf, value, ProtocolCodec.MAX_STRING_LENGTH)
}

private fun writeOptionalStringList(
    buf: WriteBuffer,
    slot: Int,
    values: List<String>?,
    varBlockStart: Int,
    field: String,
) {
    if (values == null) {
        buf.writeIntAt(slot, -1)
        return
    }
    checkArrayBounds(values.size, field)
    buf.writeIntAt(slot, buf.index - varBlockStart)
    VarInt.write(buf, values.size)
    values.forEach { ProtocolCodec.writeVarString(buf, it, ProtocolCodec.MAX_STRING_LENGTH) }
}

private inline fun <T> writeOptionalList(
    buf: WriteBuffer,
    slot: Int,
    values: List<T>?,
    varBlockStart: Int,
    field: String,
    writeItem: (T) -> Unit,
) {
    if (values == null) {
        buf.writeIntAt(slot, -1)
        return
    }
    checkArrayBounds(values.size, field)
    buf.writeIntAt(slot, buf.index - varBlockStart)
    VarInt.write(buf, values.size)
    values.forEach(writeItem)
}
