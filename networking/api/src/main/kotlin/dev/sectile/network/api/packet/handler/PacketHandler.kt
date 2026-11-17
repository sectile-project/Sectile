package dev.sectile.network.api.packet.handler

import dev.sectile.network.api.buffer.ReadBuffer
import dev.sectile.network.api.packet.PacketDefinition
import dev.sectile.network.api.player.connection.PlayerConnection

/**
 * Handles an incoming packet directly from its decoded payload [ReadBuffer].
 *
 * Reading from the buffer directly allows callers to process packets without allocating
 * intermediate packet DTO objects, while keeping handlers decoupled from Netty.
 */
interface PacketHandler {

    /** Metadata of the packet handled by this listener. */
    val definition: PacketDefinition

    /** Processes the incoming packet payload from [buffer] for [connection]. */
    fun handle(connection: PlayerConnection, buffer: ReadBuffer)
}

/** Creates a [PacketHandler] bound to [definition] and [handler]. */
inline fun packetHandler(
    definition: PacketDefinition,
    crossinline handler: (connection: PlayerConnection, buffer: ReadBuffer) -> Unit,
): PacketHandler = object : PacketHandler {
    override val definition: PacketDefinition = definition

    override fun handle(connection: PlayerConnection, buffer: ReadBuffer) {
        handler(connection, buffer)
    }
}
