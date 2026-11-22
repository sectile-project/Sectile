package dev.sectile.network.protocol.player.outbound

import dev.sectile.network.api.buffer.WriteBuffer
import dev.sectile.network.api.packet.OutboundPacket
import dev.sectile.network.api.packet.PacketDefinition
import dev.sectile.network.protocol.DataSize
import dev.sectile.network.protocol.ProtocolPacketTable
import dev.sectile.network.protocol.player.enums.StreamType

class StreamOpenResponsePacket(
    var type: StreamType = StreamType.GAME,
    var accepted: Boolean = false,
    var rejectionReason: String? = null,
) : OutboundPacket {

    override val definition: PacketDefinition = ProtocolPacketTable.STREAM_OPEN_RESPONSE

    override fun write(buf: WriteBuffer) {
        val reason = rejectionReason
        buf.writeByte(if (reason == null) 0 else 0x01)
        buf.writeByte(type.ordinal)
        buf.writeBoolean(accepted)
        if (reason != null) {
            buf.writeVarString(reason, MAX_REJECTION_REASON_LENGTH)
        }
    }

    override fun size(): Int {
        val fixed = DataSize.BYTE * 2 + DataSize.BOOLEAN
        val reason = rejectionReason ?: return fixed
        return fixed + DataSize.varStringSize(reason)
    }

    companion object {
        const val MAX_REJECTION_REASON_LENGTH: Int = DataSize.MAX_STRING_LENGTH
    }
}
