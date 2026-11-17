package dev.sectile.kernel.plugin.model

import dev.sectile.kernel.plugin.model.failure.PluginFailure
import dev.sectile.kernel.plugin.model.lifecycle.PluginState

data class PluginSnapshot(
    val state: PluginState,
    val failure: PluginFailure? = null,
    val metadata: PluginMetadata,
    val mainClass: String,
)
