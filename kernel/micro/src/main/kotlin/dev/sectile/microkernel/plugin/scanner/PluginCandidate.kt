package dev.sectile.microkernel.plugin.scanner

import dev.sectile.kernel.plugin.model.PluginId
import dev.sectile.kernel.plugin.model.PluginMetadata
import dev.sectile.microkernel.plugin.manifest.PluginManifest
import java.io.File

class PluginCandidate(
    val jarFile: File,
    val manifest: PluginManifest,
) {
    val id: PluginId
        get() = manifest.metadata.id

    val metadata: PluginMetadata
        get() = manifest.metadata
}
