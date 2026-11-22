package dev.sectile.network.protocol.player.inbound

import dev.sectile.network.api.buffer.ReadBuffer
import dev.sectile.network.api.packet.InboundPacket
import dev.sectile.network.api.packet.PacketDefinition
import dev.sectile.network.protocol.ProtocolPacketTable

class ClientReadyPacket(
    var readyForChunks: Boolean = false,
    var readyForGameplay: Boolean = false,
) : InboundPacket {

    override val definition: PacketDefinition = ProtocolPacketTable.CLIENT_READY

    override fun read(buf: ReadBuffer) {
        val flags = buf.readUnsignedByte()
        readyForChunks = flags and 0x01 != 0
        readyForGameplay = flags and 0x02 != 0
    }
}
