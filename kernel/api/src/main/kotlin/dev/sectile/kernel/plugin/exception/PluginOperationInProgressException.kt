package dev.sectile.kernel.plugin.exception

import dev.sectile.kernel.plugin.model.PluginId
import dev.sectile.kernel.plugin.model.lifecycle.PluginState

class PluginOperationInProgressException(
    pluginId: PluginId,
    currentState: PluginState,
    targetState: PluginState,
) : PluginLifecycleException(
    "Plugin $pluginId is currently in state $currentState and cannot transition to $targetState because another operation is in progress.",
)
