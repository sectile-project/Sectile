package dev.sectile.kernel.plugin.scope

interface PluginScopeFactory {
    fun acquire(): PluginScope

    fun create(): PluginScope
}
