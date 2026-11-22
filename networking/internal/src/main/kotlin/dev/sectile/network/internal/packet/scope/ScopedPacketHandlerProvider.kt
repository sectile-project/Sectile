package dev.sectile.network.internal.packet.scope

import dev.sectile.kernel.plugin.scope.provider.CloseableDependencyProvider
import dev.sectile.kernel.plugin.scope.provider.ProtectedDependencyProvider
import dev.sectile.network.api.buffer.ReadBuffer
import dev.sectile.network.api.packet.PacketDefinition
import dev.sectile.network.api.packet.handler.PacketHandler
import dev.sectile.network.api.packet.handler.PacketHandlerRegistry
import dev.sectile.network.api.player.connection.PlayerConnection
import java.util.IdentityHashMap

class ScopedPacketHandlerProvider(
    private val delegate: PacketHandlerRegistry,
    override val type: Class<PacketHandlerRegistry> = PacketHandlerRegistry::class.java,
) : PacketHandlerRegistry,
    CloseableDependencyProvider<PacketHandlerRegistry>,
    ProtectedDependencyProvider {

    private val registrations = IdentityHashMap<PacketHandler, PacketHandler>()

    override fun hasHandlers(packetId: Int): Boolean = delegate.hasHandlers(packetId)

    override fun add(handler: PacketHandler) {
        val wrapped = synchronized(registrations) {
            if (handler in registrations) return
            val entry = ScopedPacketHandler(
                delegate = handler,
                classLoader = handler.javaClass.classLoader ?: Thread.currentThread().contextClassLoader,
            )
            registrations[handler] = entry
            entry
        }
        delegate.add(wrapped)
    }

    override fun remove(handler: PacketHandler) {
        val wrapped = synchronized(registrations) {
            registrations.remove(handler)
        } ?: return
        delegate.remove(wrapped)
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

    override fun provide(): PacketHandlerRegistry = this

    override fun close() {
        val snapshot = synchronized(registrations) {
            registrations.values.toList().also { registrations.clear() }
        }
        for (wrapped in snapshot) {
            delegate.remove(wrapped)
        }
    }

    private class ScopedPacketHandler(
        private val delegate: PacketHandler,
        private val classLoader: ClassLoader?,
    ) : PacketHandler {

        override val definition: PacketDefinition
            get() = delegate.definition

        override fun handle(connection: PlayerConnection, buffer: ReadBuffer) {
            val loader = classLoader
            if (loader == null) {
                delegate.handle(connection, buffer)
                return
            }
            val thread = Thread.currentThread()
            val previous = thread.contextClassLoader
            thread.contextClassLoader = loader
            try {
                delegate.handle(connection, buffer)
            } finally {
                thread.contextClassLoader = previous
            }
        }
    }
}
