package dev.sectile.network.api.packet.handler

import dev.sectile.network.api.buffer.ReadBuffer
import dev.sectile.network.api.packet.InboundPacket
import dev.sectile.network.api.packet.PacketDefinition
import dev.sectile.network.api.player.connection.PlayerConnection

interface PacketHandlerRegistry {

    fun hasHandlers(packetId: Int): Boolean

    fun add(handler: PacketHandler)

    fun remove(handler: PacketHandler)

    fun add(vararg handlers: PacketHandler)

    fun remove(vararg handlers: PacketHandler)
}

inline fun PacketHandlerRegistry.handle(
    definition: PacketDefinition,
    crossinline block: (connection: PlayerConnection, buffer: ReadBuffer) -> Unit,
): PacketHandler = packetHandler(definition, block).also { add(it) }

inline fun <T : InboundPacket> PacketHandlerRegistry.on(
    definition: PacketDefinition,
    crossinline factory: () -> T,
    crossinline block: (connection: PlayerConnection, packet: T) -> Unit,
): PacketHandler = handle(definition) { connection, buffer ->
    val packet = factory()
    packet.read(buffer)
    block(connection, packet)
}
