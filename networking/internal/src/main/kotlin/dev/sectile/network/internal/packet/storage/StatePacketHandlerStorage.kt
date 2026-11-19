package dev.sectile.network.internal.packet.storage

import dev.sectile.network.api.buffer.ReadBuffer
import dev.sectile.network.api.packet.handler.PacketHandler
import dev.sectile.network.api.packet.handler.PacketHandlerStorage
import dev.sectile.network.api.player.connection.PlayerConnection

/**
 * Array-backed [PacketHandlerStorage] indexed directly by packet id for fast O(1) lookup.
 */
class StatePacketHandlerStorage(size: Int) : PacketHandlerStorage {

    private val packetHandlerStorage = arrayOfNulls<MutablePacketHandlerStorage>(size)

    override fun callHandlers(connection: PlayerConnection, packetId: Int, buffer: ReadBuffer) {
        requirePacketIdInRange(packetId)
        packetHandlerStorage[packetId]?.callHandlers(connection, buffer)
    }

    override fun hasHandlers(packetId: Int): Boolean = packetId in packetHandlerStorage.indices && packetHandlerStorage[packetId] != null

    @Synchronized
    override fun add(handler: PacketHandler) {
        val packetId = handler.definition.id
        requirePacketIdInRange(packetId)

        val currentStorage = packetHandlerStorage[packetId]
        packetHandlerStorage[packetId] = currentStorage?.add(handler) ?: SingleHandlerStorage(handler)
    }

    @Synchronized
    override fun remove(handler: PacketHandler) {
        val packetId = handler.definition.id
        requirePacketIdInRange(packetId)

        packetHandlerStorage[packetId] = packetHandlerStorage[packetId]?.remove(handler)
    }

    override fun add(vararg handlers: PacketHandler) {
        for (handler in handlers) {
            add(handler)
        }
    }

    override fun remove(vararg handlers: PacketHandler) {
        for (handler in handlers) {
            remove(handler)
        }
    }

    @Synchronized
    override fun clear(packetId: Int) {
        requirePacketIdInRange(packetId)
        packetHandlerStorage[packetId] = null
    }

    private fun requirePacketIdInRange(packetId: Int) {
        if (packetId < 0 || packetId >= packetHandlerStorage.size) {
            throw ArrayIndexOutOfBoundsException(
                "Packet id needs to be in the range 0-${packetHandlerStorage.size - 1} but found $packetId",
            )
        }
    }
}
