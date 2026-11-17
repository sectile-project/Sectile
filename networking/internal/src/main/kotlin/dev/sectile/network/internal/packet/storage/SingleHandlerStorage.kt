package dev.sectile.network.internal.packet.storage

import dev.sectile.network.api.buffer.ReadBuffer
import dev.sectile.network.api.packet.handler.PacketHandler
import dev.sectile.network.api.player.connection.PlayerConnection

internal class SingleHandlerStorage(private val handler: PacketHandler) : MutablePacketHandlerStorage {

    override fun callHandlers(connection: PlayerConnection, buffer: ReadBuffer) {
        handler.handle(connection, buffer)
    }

    override fun add(handler: PacketHandler): MutablePacketHandlerStorage = ArrayHandlerStorage(arrayOf(this.handler, handler))

    override fun remove(handler: PacketHandler): MutablePacketHandlerStorage? = if (this.handler == handler) null else this

    override fun add(vararg handlers: PacketHandler): MutablePacketHandlerStorage {
        if (handlers.isEmpty()) return this
        return ArrayHandlerStorage(arrayOf(handler, *handlers))
    }

    override fun remove(vararg handlers: PacketHandler): MutablePacketHandlerStorage? {
        var storage: MutablePacketHandlerStorage? = this
        for (target in handlers) {
            storage = storage?.remove(target)
        }
        return storage
    }
}
