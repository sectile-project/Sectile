package dev.sectile.engine.world.handler

import dev.sectile.engine.internal.InternalPlayer
import dev.sectile.engine.internal.registry.InternalPlayerRegistry
import dev.sectile.network.api.buffer.ReadBuffer
import dev.sectile.network.api.packet.PacketDefinition
import dev.sectile.network.api.packet.handler.PacketHandler
import dev.sectile.network.api.player.connection.PlayerConnection
import dev.sectile.network.protocol.ProtocolPacketTable

object MovementHandler : PacketHandler {
    override val definition: PacketDefinition = ProtocolPacketTable.CLIENT_MOVEMENT

    private const val MOVEMENT_ABS_POS_OFFSET = 12
    private const val MOVEMENT_LOOK_OFFSET = 48

    override fun handle(
        connection: PlayerConnection,
        buffer: ReadBuffer,
    ) {
        val player = InternalPlayerRegistry.byConnection(connection) as? InternalPlayer ?: return

        val base = buffer.index
        val nullBits = buffer.readUnsignedByte()
        val hasPosition = (nullBits and 0x04) != 0
        val hasLook = (nullBits and 0x10) != 0
        if (!hasPosition && !hasLook) return

        if (hasPosition) {
            buffer.skipTo(base + MOVEMENT_ABS_POS_OFFSET)
            player.x = buffer.readDouble()
            player.y = buffer.readDouble()
            player.z = buffer.readDouble()
        }

        if (hasLook) {
            buffer.skipTo(base + MOVEMENT_LOOK_OFFSET)
            player.yaw = buffer.readFloat()
            player.pitch = buffer.readFloat()
        }
    }
}
