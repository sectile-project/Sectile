package dev.sectile.network.protocol.auth.outbound

import dev.sectile.network.api.buffer.WriteBuffer
import dev.sectile.network.api.packet.OutboundPacket
import dev.sectile.network.api.packet.PacketDefinition
import dev.sectile.network.protocol.ProtocolPacketTable

class PasswordAcceptedPacket : OutboundPacket {

    override val definition: PacketDefinition = ProtocolPacketTable.PASSWORD_ACCEPTED

    override fun write(buf: WriteBuffer) = Unit

    override fun size(): Int = 0
}
