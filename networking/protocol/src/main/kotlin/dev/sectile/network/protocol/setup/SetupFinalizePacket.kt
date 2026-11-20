package dev.sectile.network.protocol.setup

import dev.sectile.network.api.buffer.ReadBuffer
import dev.sectile.network.api.packet.InboundPacket
import dev.sectile.network.api.packet.PacketDefinition
import dev.sectile.network.protocol.ProtocolPacketTable

/**
 * Inbound `SetupFinalize` packet (id 33, client -> server).
 *
 * Client notification that setup loading has finished and it is ready to join the world.
 * Empty payload.
 */
class SetupFinalizePacket : InboundPacket {

    override val definition: PacketDefinition
        get() = ProtocolPacketTable.SETUP_FINALIZE

    override fun read(buf: ReadBuffer) = Unit
}
