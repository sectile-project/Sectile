package dev.sectile.network.protocol.player

import dev.sectile.network.api.buffer.ReadBuffer
import dev.sectile.network.api.packet.InboundPacket
import dev.sectile.network.api.packet.PacketDefinition
import dev.sectile.network.protocol.ProtocolPacketTable

/**
 * Inbound `ClientReady` packet (id 105, client -> server).
 *
 * Indicates the client is ready for chunks (`0x01`) and/or gameplay (`0x02`).
 */
class ClientReadyPacket(
    var readyForChunks: Boolean = false,
    var readyForGameplay: Boolean = false,
) : InboundPacket {

    override val definition: PacketDefinition
        get() = ProtocolPacketTable.CLIENT_READY

    override fun read(buf: ReadBuffer) {
        val flags = buf.readUnsignedByte()
        readyForChunks = flags and 0x01 != 0
        readyForGameplay = flags and 0x02 != 0
    }
}
