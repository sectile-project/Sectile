package dev.sectile.network.protocol.player.inbound

import dev.sectile.network.api.buffer.ReadBuffer
import dev.sectile.network.api.packet.InboundPacket
import dev.sectile.network.api.packet.PacketDefinition
import dev.sectile.network.protocol.ProtocolPacketTable

class SetupFinalizePacket : InboundPacket {

    override val definition: PacketDefinition = ProtocolPacketTable.SETUP_FINALIZE

    override fun read(buf: ReadBuffer) = Unit
}
