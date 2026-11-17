package dev.sectile.kernel.plugin.exception

open class PluginDependencyException(
    message: String,
) : PluginLifecycleException(message)
