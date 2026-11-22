package dev.sectile.engine.api.world

interface WorldCatalog {
    val default: World?

    fun forEach(action: (World) -> Unit)

    fun all(): Collection<World>

    fun byId(id: String): World?

    fun byName(name: String): World?

    fun byNameExact(name: String): World?
}
