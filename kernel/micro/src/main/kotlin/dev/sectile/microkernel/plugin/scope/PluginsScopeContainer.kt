package dev.sectile.microkernel.plugin.scope

import dev.sectile.kernel.plugin.scope.PluginScope
import dev.sectile.microkernel.Microkernel
import java.util.concurrent.ConcurrentHashMap

object PluginsScopeContainer {
    private val scopes = ConcurrentHashMap<ClassLoader, PluginScope>()

    fun getOrCreateScope(
        classLoader: ClassLoader,
        supplyFunction: () -> PluginScope,
    ): PluginScope = scopes.computeIfAbsent(classLoader) {
        supplyFunction()
    }

    fun removeScope(classLoader: ClassLoader): PluginScope? = scopes.remove(classLoader)
}
