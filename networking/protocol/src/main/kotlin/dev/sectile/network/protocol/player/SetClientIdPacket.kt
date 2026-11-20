package dev.sectile.network.protocol.player

import dev.sectile.network.api.buffer.WriteBuffer
import dev.sectile.network.api.packet.OutboundPacket
import dev.sectile.network.api.packet.PacketDefinition
import dev.sectile.network.protocol.ProtocolPacketTable

/**
 * Outbound `SetClientId` packet (id 100, server -> client).
 *
 * Assigns the entity ID representing the local player.
 */
class SetClientIdPacket(
    var clientId: Int = 0,
) : OutboundPacket {

    override val definition: PacketDefinition
        get() = ProtocolPacketTable.SET_CLIENT_ID

    override fun write(buf: WriteBuffer) {
        buf.writeInt(clientId)
    }

    override fun size(): Int = ProtocolPacketTable.SET_CLIENT_ID.maxSize
}
