package dev.sectile.microkernel.plugin.library.exception

import dev.sectile.kernel.plugin.exception.PluginLifecycleException

class PluginLibraryResolutionException(
    message: String,
    cause: Throwable? = null,
) : PluginLifecycleException(message, cause)
