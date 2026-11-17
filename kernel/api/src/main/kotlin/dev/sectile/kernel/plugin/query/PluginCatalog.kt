package dev.sectile.kernel.plugin.query

import dev.sectile.kernel.plugin.model.PluginId
import dev.sectile.kernel.plugin.model.PluginSnapshot

interface PluginCatalog {
    operator fun get(id: PluginId): PluginSnapshot?

    fun getFromCurrentScope(): PluginSnapshot?

    fun findAll(): Collection<PluginSnapshot>

    fun contains(id: PluginId): Boolean
}
