package dev.sectile.kernel.plugin.control

import dev.sectile.kernel.plugin.exception.PluginNotFoundException
import dev.sectile.kernel.plugin.exception.PluginOperationInProgressException
import dev.sectile.kernel.plugin.exception.PluginTransitionNotAllowedException
import dev.sectile.kernel.plugin.model.PluginId

interface PluginLifecycleControl {
    @Throws(
        PluginNotFoundException::class,
        PluginTransitionNotAllowedException::class,
        PluginOperationInProgressException::class,
    )
    fun load(id: PluginId)

    @Throws(
        PluginNotFoundException::class,
        PluginTransitionNotAllowedException::class,
        PluginOperationInProgressException::class,
    )
    fun enable(id: PluginId)

    @Throws(
        PluginNotFoundException::class,
        PluginTransitionNotAllowedException::class,
        PluginOperationInProgressException::class,
    )
    fun disable(id: PluginId)

    @Throws(
        PluginNotFoundException::class,
        PluginTransitionNotAllowedException::class,
        PluginOperationInProgressException::class,
    )
    fun reload(id: PluginId)

    fun reloadAll()
}
