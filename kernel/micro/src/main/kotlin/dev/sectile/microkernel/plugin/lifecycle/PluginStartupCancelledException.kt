package dev.sectile.microkernel.plugin.lifecycle

import dev.sectile.kernel.plugin.model.PluginId
import dev.sectile.kernel.plugin.model.lifecycle.PluginState

internal class PluginStartupCancelledException(
    val pluginId: PluginId,
    val phase: PluginState,
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
