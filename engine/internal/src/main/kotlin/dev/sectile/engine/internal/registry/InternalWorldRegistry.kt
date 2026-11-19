package dev.sectile.engine.internal.registry

import dev.sectile.engine.api.world.World
import dev.sectile.engine.api.world.WorldRegistry
import java.util.Locale.getDefault
import java.util.concurrent.CopyOnWriteArraySet

object InternalWorldRegistry : WorldRegistry {

    private val WORLDS = CopyOnWriteArraySet<World>()

    override fun register(world: World) {
        if (WORLDS.contains(world)) {
            throw IllegalStateException("World ${world.name} is already registered.")
        }
        WORLDS.add(world)
    }

    override fun unregister(world: World) {
        WORLDS.remove(world)
    }

    override val default: World?
        get() = WORLDS.firstOrNull()

    override fun forEach(action: (World) -> Unit) {
        WORLDS.forEach(action)
    }

    override fun all(): Collection<World> = WORLDS

    override fun byId(id: String): World? = WORLDS.firstOrNull { it.id == id }

    override fun byName(name: String): World? {
        var found: World? = null
        var delta = Int.MAX_VALUE
        val lowerName = name.lowercase(getDefault())

        for (world in WORLDS) {
            if (world.name.lowercase(getDefault()).startsWith(lowerName)) {
                val curDelta = world.name.length - lowerName.length
                if (curDelta < delta) {
                    found = world
                    delta = curDelta
                }
                if (curDelta == 0) break
            }
        }
        return found
    }

    override fun byNameExact(name: String): World? = WORLDS.firstOrNull { it.name == name }
}
