package dev.sectile.microkernel.plugin.manifest

import dev.sectile.kernel.plugin.model.PluginMetadata

class PluginManifest(
    val metadata: PluginMetadata,
    val mainClass: String,
    val boostrapClass: String?,
)
