package dev.sectile.kernel.plugin.exception

import dev.sectile.kernel.plugin.model.PluginId
import dev.sectile.kernel.plugin.model.lifecycle.PluginState

class PluginLifecycleExecutionException(
    pluginId: PluginId,
    phase: PluginState,
    cause: Throwable,
) : PluginLifecycleException(
    "Plugin $pluginId failed during $phase.",
    cause,
)
