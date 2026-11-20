package dev.sectile.network.protocol.interfaceui

import dev.sectile.network.api.buffer.ReadBuffer
import dev.sectile.network.api.packet.InboundPacket
import dev.sectile.network.api.packet.PacketDefinition
import dev.sectile.network.protocol.ProtocolCodec
import dev.sectile.network.protocol.ProtocolPacketTable

class ArgValuesRequestPacket(
    var argTypeId: String? = null,
    var partial: String? = null,
) : InboundPacket {

    override val definition: PacketDefinition
        get() = ProtocolPacketTable.ARG_VALUES_REQUEST

    override fun read(buf: ReadBuffer) {
        val base = buf.index
        val nullBits = buf.readUnsignedByte()
        val varBlockStart = base + VARIABLE_BLOCK_START
        buf.skipTo(varBlockStart)

        if (nullBits and 0x01 != 0) {
            ProtocolCodec.requireSlot(buf, base + 1, buf.index - varBlockStart, "ArgTypeId")
            argTypeId = ProtocolCodec.readVarString(buf, ProtocolCodec.MAX_STRING_LENGTH, "ArgTypeId")
        } else {
            ProtocolCodec.requireSlot(buf, base + 1, -1, "ArgTypeId")
            argTypeId = null
        }

        if (nullBits and 0x02 != 0) {
            ProtocolCodec.requireSlot(buf, base + 5, buf.index - varBlockStart, "Partial")
            partial = ProtocolCodec.readVarString(buf, ProtocolCodec.MAX_STRING_LENGTH, "Partial")
        } else {
            ProtocolCodec.requireSlot(buf, base + 5, -1, "Partial")
            partial = null
        }
    }

    companion object {
        const val VARIABLE_BLOCK_START: Int = 9
    }
}
