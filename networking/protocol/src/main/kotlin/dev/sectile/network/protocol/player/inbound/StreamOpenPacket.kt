package dev.sectile.network.protocol.player.inbound

import dev.sectile.network.api.buffer.ReadBuffer
import dev.sectile.network.api.packet.InboundPacket
import dev.sectile.network.api.packet.PacketDefinition
import dev.sectile.network.protocol.ProtocolFormatException
import dev.sectile.network.protocol.ProtocolPacketTable
import dev.sectile.network.protocol.player.enums.StreamType

class StreamOpenPacket(
    var type: StreamType = StreamType.GAME,
) : InboundPacket {

    override val definition: PacketDefinition = ProtocolPacketTable.STREAM_OPEN

    override fun read(buf: ReadBuffer) {
        val ordinal = buf.readUnsignedByte()
        type = StreamType.of(ordinal)
            ?: throw ProtocolFormatException("Invalid enum value 'StreamType': $ordinal")
    }
}
