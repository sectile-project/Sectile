package dev.sectile.network.protocol.command.inbound

import dev.sectile.network.api.buffer.ReadBuffer
import dev.sectile.network.api.buffer.readSlot
import dev.sectile.network.api.packet.InboundPacket
import dev.sectile.network.api.packet.PacketDefinition
import dev.sectile.network.protocol.DataSize
import dev.sectile.network.protocol.ProtocolPacketTable

class ArgValuesRequestPacket(
    var argTypeId: String? = null,
    var partial: String? = null,
) : InboundPacket {

    override val definition: PacketDefinition = ProtocolPacketTable.ARG_VALUES_REQUEST

    override fun read(buf: ReadBuffer) {
        val base = buf.index
        val nullBits = buf.readUnsignedByte()
        val varBlockStart = base + VARIABLE_BLOCK_START
        buf.skipTo(varBlockStart)

        argTypeId = buf.readSlot(base + 1, varBlockStart, nullBits and 0x01 != 0, "ArgTypeId") {
            buf.readVarString(DataSize.MAX_STRING_LENGTH, "ArgTypeId")
        }
        partial = buf.readSlot(base + 5, varBlockStart, nullBits and 0x02 != 0, "Partial") {
            buf.readVarString(DataSize.MAX_STRING_LENGTH, "Partial")
        }
    }

    companion object {
        const val VARIABLE_BLOCK_START: Int = 9
    }
}
