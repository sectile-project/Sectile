package dev.sectile.microkernel.plugin.scope.provider

import dev.sectile.kernel.plugin.scope.provider.CloseableDependencyProvider
import dev.sectile.kernel.plugin.scope.provider.ProtectedDependencyProvider
import dev.sectile.kernel.service.ServiceRegistration
import dev.sectile.kernel.service.ServiceRegistry
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean

class ScopedServiceRegistryProvider(
    private val delegate: ServiceRegistry,
    override val type: Class<ServiceRegistry> = ServiceRegistry::class.java,
) : ServiceRegistry,
    CloseableDependencyProvider<ServiceRegistry>,
    ProtectedDependencyProvider {

    private val registrations: MutableList<ScopedRegistration<*>> =
        Collections.synchronizedList(mutableListOf())

    override fun <T : Any> get(type: Class<T>): T? = delegate[type]

    override fun <T : Any> all(type: Class<T>): List<T> = delegate.all(type)

    override fun contains(type: Class<*>): Boolean = delegate.contains(type)

    override fun <T : Any> register(
        type: Class<T>,
        provider: T,
        priority: Int,
    ): ServiceRegistration<T> {
        val wrapped = ScopedRegistration(delegate.register(type, provider, priority))
        registrations.add(wrapped)
        return wrapped
    }

    override fun <T : Any> unregister(
        type: Class<T>,
        provider: T,
    ): Boolean {
        val target = synchronized(registrations) {
            registrations.lastOrNull { it.type == type && it.provider == provider }
                ?.also { registrations.remove(it) }
        } ?: return false
        target.unregister()
        return true
    }

    override fun provide(): ServiceRegistry = this

    override fun close() {
        val snapshot = synchronized(registrations) {
            registrations.toList().asReversed().also { registrations.clear() }
        }
        snapshot.forEach { it.unregister() }
    }

    private inner class ScopedRegistration<T : Any>(
        private val raw: ServiceRegistration<T>,
    ) : ServiceRegistration<T> {
        private val unregistered = AtomicBoolean(false)

        override val type: Class<T> get() = raw.type
        override val provider: T get() = raw.provider
        override val priority: Int get() = raw.priority

        override fun unregister() {
            synchronized(registrations) {
                registrations.remove(this)
            }
            if (unregistered.compareAndSet(false, true)) {
                raw.unregister()
            }
        }
    }
}
