package dev.sectile.network.api.packet.handler

import dev.sectile.network.api.buffer.ReadBuffer
import dev.sectile.network.api.player.connection.PlayerConnection

/**
 * Registry of [PacketHandler] instances indexed by packet id for a connection stage.
 */
interface PacketHandlerStorage : PacketHandlerRegistry {

    /** Dispatches the payload in [buffer] for [packetId] to all registered handlers. */
    fun callHandlers(connection: PlayerConnection, packetId: Int, buffer: ReadBuffer)

    /** Removes all registered handlers for [packetId]. */
    fun clear(packetId: Int)
}
