package dev.sectile.network.api.packet.handler

import dev.sectile.network.api.buffer.ReadBuffer
import dev.sectile.network.api.packet.InboundPacket
import dev.sectile.network.api.packet.PacketDefinition
import dev.sectile.network.api.player.connection.PlayerConnection

/**
 * Registry of [PacketHandler] instances indexed by packet id for a connection stage.
 */
interface PacketHandlerStorage {

    /** Dispatches the payload in [buffer] for [packetId] to all registered handlers. */
    fun callHandlers(connection: PlayerConnection, packetId: Int, buffer: ReadBuffer)

    /** Returns `true` if at least one handler is registered for [packetId]. */
    fun hasHandlers(packetId: Int): Boolean

    fun add(handler: PacketHandler)

    fun remove(handler: PacketHandler)

    fun add(vararg handlers: PacketHandler)

    fun remove(vararg handlers: PacketHandler)

    /** Removes all registered handlers for [packetId]. */
    fun clear(packetId: Int)
}

/**
 * Convenience helper that creates and registers a [PacketHandler] reading directly from [ReadBuffer].
 */
inline fun PacketHandlerStorage.handle(
    definition: PacketDefinition,
    crossinline block: (connection: PlayerConnection, buffer: ReadBuffer) -> Unit,
): PacketHandler = packetHandler(definition, block).also { add(it) }

/**
 * Convenience helper that deserializes an [InboundPacket] using [factory] and passes it to [block].
 */
inline fun <T : InboundPacket> PacketHandlerStorage.on(
    definition: PacketDefinition,
    crossinline factory: () -> T,
    crossinline block: (connection: PlayerConnection, packet: T) -> Unit,
): PacketHandler = handle(definition) { connection, buffer ->
    val packet = factory()
    packet.read(buffer)
    block(connection, packet)
}
