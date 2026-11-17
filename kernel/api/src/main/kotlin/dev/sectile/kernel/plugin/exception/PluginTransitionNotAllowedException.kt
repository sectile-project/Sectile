package dev.sectile.kernel.plugin.exception

import dev.sectile.kernel.plugin.model.lifecycle.PluginState

class PluginTransitionNotAllowedException(
    currentState: PluginState,
    targetState: PluginState,
    errorMessage: String? = null,
) : PluginLifecycleException("Plugin transition from $currentState to $targetState is not allowed. ${errorMessage ?: ""}")
