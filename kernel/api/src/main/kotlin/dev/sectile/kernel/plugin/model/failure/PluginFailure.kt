package dev.sectile.kernel.plugin.model.failure

import dev.sectile.kernel.plugin.model.lifecycle.PluginState
import java.time.Instant

data class PluginFailure(
    val state: PluginState,
    val exception: PluginExceptionDetails? = null,
    val timestamp: Instant = Instant.now(),
)
