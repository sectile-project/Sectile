package dev.sectile.network.protocol.command.outbound

import dev.sectile.network.api.buffer.WriteBuffer
import dev.sectile.network.api.packet.OutboundPacket
import dev.sectile.network.api.packet.PacketDefinition
import dev.sectile.network.protocol.DataSize
import dev.sectile.network.protocol.ProtocolPacketTable
import dev.sectile.network.protocol.command.structures.writeListSlot
import dev.sectile.network.protocol.command.structures.writeStringListSlot
import dev.sectile.network.protocol.command.structures.writeStringSlot

class ArgValuesResponsePacket(
    var argTypeId: String? = null,
    var values: List<String>? = null,
    var continuations: List<Boolean>? = null,
    var isComplete: Boolean = true,
) : OutboundPacket {

    override val definition: PacketDefinition = ProtocolPacketTable.ARG_VALUES_RESPONSE

    override fun write(buf: WriteBuffer) {
        var nullBits = 0
        if (argTypeId != null) nullBits = nullBits or 0x01
        if (values != null) nullBits = nullBits or 0x02
        if (continuations != null) nullBits = nullBits or 0x04

        buf.writeByte(nullBits)
        buf.writeBoolean(isComplete)

        val argTypeIdSlot = buf.reserveOffset()
        val valuesSlot = buf.reserveOffset()
        val continuationsSlot = buf.reserveOffset()
        val varBlockStart = buf.index

        buf.writeStringSlot(argTypeIdSlot, varBlockStart, argTypeId)
        buf.writeStringListSlot(valuesSlot, varBlockStart, values, "Values")
        buf.writeListSlot(continuationsSlot, varBlockStart, continuations, "Continuations") {
            buf.writeBoolean(it)
        }
    }

    override fun size(): Int {
        var total = VARIABLE_BLOCK_START
        argTypeId?.let { total += DataSize.varStringSize(it) }
        values?.let { total += DataSize.stringListSize(it) }
        continuations?.let { total += DataSize.varListSize(it.size, DataSize.BOOLEAN) }
        return total
    }

    companion object {
        const val VARIABLE_BLOCK_START: Int = 14
    }
}
