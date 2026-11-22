package dev.sectile.microkernel.plugin.catalog

import dev.sectile.kernel.plugin.model.PluginId
import dev.sectile.kernel.plugin.model.PluginMetadata
import dev.sectile.kernel.plugin.model.PluginSnapshot
import dev.sectile.kernel.plugin.model.failure.PluginFailure
import dev.sectile.kernel.plugin.model.lifecycle.PluginState
import dev.sectile.kernel.plugin.spi.lifecycle.PluginLifecycle
import dev.sectile.microkernel.plugin.classloading.PluginClassLoader
import dev.sectile.microkernel.plugin.scanner.PluginCandidate

internal class PluginEntry(
    @Volatile
    var candidate: PluginCandidate,
    initialState: PluginState,
) {
    val id: PluginId
        get() = metadata.id

    val metadata: PluginMetadata
        get() = candidate.manifest.metadata

    @Volatile
    var state: PluginState = initialState

    @Volatile
    var failure: PluginFailure? = null

    @Volatile
    var lifecycle: PluginLifecycle? = null

    @Volatile
    var classLoader: PluginClassLoader? = null

    fun snapshot(): PluginSnapshot = PluginSnapshot(state, failure, metadata, candidate.manifest.mainClass)

    override fun toString(): String = id.toString()
}
