package dev.sectile.kernel.plugin.scope

interface PluginScopeFactory {
    fun acquire(): PluginScope

    fun acquire(owner: Any): PluginScope

    fun acquire(classLoader: ClassLoader): PluginScope
}
