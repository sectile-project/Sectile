package dev.sectile.network.protocol.stream

import dev.sectile.network.api.buffer.WriteBuffer
import dev.sectile.network.api.packet.OutboundPacket
import dev.sectile.network.api.packet.PacketDefinition
import dev.sectile.network.protocol.ProtocolCodec
import dev.sectile.network.protocol.ProtocolPacketTable
import dev.sectile.network.protocol.VarInt
import dev.sectile.network.protocol.enums.StreamType

/**
 * Outbound `StreamOpenResponse` packet (id 461, server -> client).
 *
 * Layout: nullability bitmask (1 byte), stream type (1 byte), accepted flag (1 byte), and
 * the optional rejection reason inline without an offset slot (single variable field).
 */
class StreamOpenResponsePacket(
    var type: StreamType = StreamType.GAME,
    var accepted: Boolean = false,
    var rejectionReason: String? = null,
) : OutboundPacket {

    override val definition: PacketDefinition
        get() = ProtocolPacketTable.STREAM_OPEN_RESPONSE

    override fun write(buf: WriteBuffer) {
        buf.writeByte(if (rejectionReason == null) 0 else 0x01)
        buf.writeByte(type.ordinal)
        buf.writeBoolean(accepted)
        if (rejectionReason != null) {
            ProtocolCodec.writeVarString(buf, rejectionReason!!, MAX_REJECTION_REASON_LENGTH)
        }
    }

    override fun size(): Int {
        val reason = rejectionReason ?: return 3
        val bytes = reason.toByteArray(Charsets.UTF_8).size
        return 3 + VarInt.sizeOf(bytes) + bytes
    }

    companion object {
        /** Maximum rejection reason string length. */
        const val MAX_REJECTION_REASON_LENGTH: Int = 4_096_000
    }
}
