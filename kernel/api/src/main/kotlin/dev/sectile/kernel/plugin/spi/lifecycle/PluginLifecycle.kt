package dev.sectile.kernel.plugin.spi.lifecycle

interface PluginLifecycle {
    fun onLoad()

    fun onEnable()

    fun onDisable()
}
