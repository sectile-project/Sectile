package dev.sectile.network.protocol.connection.inbound

import dev.sectile.network.api.buffer.ReadBuffer
import dev.sectile.network.api.packet.InboundPacket
import dev.sectile.network.api.packet.PacketDefinition
import dev.sectile.network.protocol.ProtocolFormatException
import dev.sectile.network.protocol.ProtocolPacketTable
import dev.sectile.network.protocol.connection.enums.PongType
import dev.sectile.network.protocol.shared.InstantData

class PongPacket(
    var id: Int = 0,
    var time: InstantData = InstantData(),
    var type: PongType = PongType.RAW,
    var packetQueueSize: Short = 0,
) : InboundPacket {

    override val definition: PacketDefinition = ProtocolPacketTable.PONG

    override fun read(buf: ReadBuffer) {
        id = buf.readInt()
        time.read(buf)
        val typeOrdinal = buf.readUnsignedByte()
        type = PongType.of(typeOrdinal)
            ?: throw ProtocolFormatException("Invalid enum value 'PongType': $typeOrdinal")
        packetQueueSize = buf.readShort()
    }
}
