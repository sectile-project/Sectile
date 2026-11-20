package dev.sectile.network.protocol.connection

import dev.sectile.network.api.buffer.WriteBuffer
import dev.sectile.network.api.packet.OutboundPacket
import dev.sectile.network.api.packet.PacketDefinition
import dev.sectile.network.protocol.ProtocolPacketTable

/**
 * Outbound `RequestInsecurePlayerOptions` packet (id 364, server -> client).
 *
 * Requests the player's options (username and skin) in insecure mode. Empty payload.
 */
class RequestInsecurePlayerOptionsPacket : OutboundPacket {

    override val definition: PacketDefinition
        get() = ProtocolPacketTable.REQUEST_INSECURE_PLAYER_OPTIONS

    override fun write(buf: WriteBuffer) = Unit

    override fun size(): Int = 0
}
