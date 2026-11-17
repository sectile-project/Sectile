package dev.sectile.network.internal.packet.storage

import dev.sectile.network.api.buffer.ReadBuffer
import dev.sectile.network.api.packet.handler.PacketHandler
import dev.sectile.network.api.player.connection.PlayerConnection

internal interface MutablePacketHandlerStorage {
    fun callHandlers(connection: PlayerConnection, buffer: ReadBuffer)

    fun add(handler: PacketHandler): MutablePacketHandlerStorage
    fun remove(handler: PacketHandler): MutablePacketHandlerStorage?

    fun add(vararg handlers: PacketHandler): MutablePacketHandlerStorage
    fun remove(vararg handlers: PacketHandler): MutablePacketHandlerStorage?
}
