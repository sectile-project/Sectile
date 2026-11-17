package dev.sectile.kernel.plugin.exception

open class PluginLifecycleException(
    message: String,
    cause: Throwable? = null,
) : PluginException(message, cause)
