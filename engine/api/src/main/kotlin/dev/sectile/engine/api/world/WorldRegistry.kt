package dev.sectile.engine.api.world

interface WorldRegistry : WorldCatalog {
    fun register(world: World)

    fun unregister(world: World)
}
