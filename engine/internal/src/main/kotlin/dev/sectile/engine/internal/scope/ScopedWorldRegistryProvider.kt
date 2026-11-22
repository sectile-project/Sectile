package dev.sectile.engine.internal.scope

import dev.sectile.engine.api.world.World
import dev.sectile.engine.api.world.WorldRegistry
import dev.sectile.kernel.plugin.scope.provider.CloseableDependencyProvider
import dev.sectile.kernel.plugin.scope.provider.ProtectedDependencyProvider
import java.util.Collections

class ScopedWorldRegistryProvider(
    private val delegate: WorldRegistry,
    override val type: Class<WorldRegistry> = WorldRegistry::class.java,
) : WorldRegistry,
    CloseableDependencyProvider<WorldRegistry>,
    ProtectedDependencyProvider {

    private val worlds: MutableSet<World> = Collections.synchronizedSet(linkedSetOf())

    override val default: World?
        get() = delegate.default

    override fun register(world: World) {
        delegate.register(world)
        worlds.add(world)
    }

    override fun unregister(world: World) {
        if (worlds.remove(world)) {
            delegate.unregister(world)
        }
    }

    override fun forEach(action: (World) -> Unit) = delegate.forEach(action)

    override fun all(): Collection<World> = delegate.all()

    override fun byId(id: String): World? = delegate.byId(id)

    override fun byName(name: String): World? = delegate.byName(name)

    override fun byNameExact(name: String): World? = delegate.byNameExact(name)

    override fun provide(): WorldRegistry = this

    override fun close() {
        val snapshot = synchronized(worlds) {
            worlds.toList().also { worlds.clear() }
        }
        snapshot.forEach(delegate::unregister)
    }
}
