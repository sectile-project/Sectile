package dev.sectile.microkernel.plugin.catalog

import dev.sectile.kernel.plugin.exception.PluginDependencyException
import dev.sectile.kernel.plugin.model.PluginId
import dev.sectile.kernel.plugin.model.PluginSnapshot
import dev.sectile.kernel.plugin.model.lifecycle.PluginState
import dev.sectile.kernel.plugin.query.PluginCatalog
import dev.sectile.microkernel.plugin.classloading.PluginClassLoader
import dev.sectile.microkernel.plugin.scanner.PluginCandidate
import java.util.concurrent.ConcurrentHashMap

internal class DefaultPluginCatalog : PluginCatalog {
    private val plugins = ConcurrentHashMap<PluginId, PluginEntry>()

    fun register(
        candidate: PluginCandidate,
        state: PluginState,
    ): PluginEntry {
        val id = candidate.manifest.metadata.id
        val entry = PluginEntry(candidate, state)

        val existing = plugins.putIfAbsent(id, entry)
        if (existing != null) {
            throw PluginDependencyException("Plugin '$id' is already registered.")
        }
        return entry
    }

    fun entry(id: PluginId): PluginEntry? = plugins[id]
    fun entries(): List<PluginEntry> = plugins.values.toList()

    override operator fun get(id: PluginId): PluginSnapshot? = entry(id)?.snapshot()

    override fun getFromCurrentScope(): PluginSnapshot? {
        val loader = Thread.currentThread().contextClassLoader
        if (loader is PluginClassLoader) {
            return plugins[loader.id]?.snapshot()
        }
        return null
    }

    override fun findAll(): Collection<PluginSnapshot> = entries().map { it.snapshot() }
    override fun contains(id: PluginId): Boolean = plugins.containsKey(id)
}
