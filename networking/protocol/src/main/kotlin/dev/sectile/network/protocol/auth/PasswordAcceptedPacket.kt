package dev.sectile.network.protocol.auth

import dev.sectile.network.api.buffer.WriteBuffer
import dev.sectile.network.api.packet.OutboundPacket
import dev.sectile.network.api.packet.PacketDefinition
import dev.sectile.network.protocol.ProtocolPacketTable

/**
 * Outbound `PasswordAccepted` packet (id 16, server -> client).
 *
 * Confirms that the client's password was accepted. Empty payload.
 */
class PasswordAcceptedPacket : OutboundPacket {

    override val definition: PacketDefinition
        get() = ProtocolPacketTable.PASSWORD_ACCEPTED

    override fun write(buf: WriteBuffer) = Unit

    override fun size(): Int = 0
}
