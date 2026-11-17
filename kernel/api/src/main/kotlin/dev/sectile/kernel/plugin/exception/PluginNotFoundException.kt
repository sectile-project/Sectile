package dev.sectile.kernel.plugin.exception

import dev.sectile.kernel.plugin.model.PluginId

class PluginNotFoundException(
    id: PluginId,
    cause: Throwable? = null,
) : PluginException(id.value, cause)
