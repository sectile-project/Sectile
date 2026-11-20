package dev.sectile.network.protocol.stream

import dev.sectile.network.api.buffer.ReadBuffer
import dev.sectile.network.api.packet.InboundPacket
import dev.sectile.network.api.packet.PacketDefinition
import dev.sectile.network.protocol.ProtocolFormatException
import dev.sectile.network.protocol.ProtocolPacketTable
import dev.sectile.network.protocol.enums.StreamType

/**
 * Inbound `StreamOpen` packet (id 460, client -> server).
 *
 * First packet sent on an auxiliary QUIC stream opened by the client, declaring its [StreamType].
 * Fixed 1-byte payload.
 */
class StreamOpenPacket(
    var type: StreamType = StreamType.GAME,
) : InboundPacket {

    override val definition: PacketDefinition
        get() = ProtocolPacketTable.STREAM_OPEN

    override fun read(buf: ReadBuffer) {
        val ordinal = buf.readUnsignedByte()
        type = StreamType.of(ordinal)
            ?: throw ProtocolFormatException("Invalid enum value 'StreamType': $ordinal")
    }
}
