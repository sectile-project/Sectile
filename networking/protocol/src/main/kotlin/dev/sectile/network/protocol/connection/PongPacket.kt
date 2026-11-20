package dev.sectile.network.protocol.connection

import dev.sectile.network.api.buffer.ReadBuffer
import dev.sectile.network.api.packet.InboundPacket
import dev.sectile.network.api.packet.PacketDefinition
import dev.sectile.network.protocol.ProtocolFormatException
import dev.sectile.network.protocol.ProtocolPacketTable
import dev.sectile.network.protocol.enums.PongType
import dev.sectile.network.protocol.structures.InstantData

/**
 * Inbound `Pong` packet (id 4, client -> server).
 *
 * Fixed 19-byte payload: ping id (int LE), timestamp (12 bytes), pong type (1 byte), and
 * client packet queue size (short LE).
 */
class PongPacket(
    var id: Int = 0,
    var time: InstantData = InstantData(),
    var type: PongType = PongType.RAW,
    var packetQueueSize: Short = 0,
) : InboundPacket {

    override val definition: PacketDefinition
        get() = ProtocolPacketTable.PONG

    override fun read(buf: ReadBuffer) {
        id = buf.readInt()
        time.read(buf)
        val typeOrdinal = buf.readUnsignedByte()
        type = PongType.of(typeOrdinal)
            ?: throw ProtocolFormatException("Invalid enum value 'PongType': $typeOrdinal")
        packetQueueSize = buf.readShort()
    }
}
