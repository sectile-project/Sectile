package dev.sectile.microkernel.plugin.scope

import dev.sectile.kernel.plugin.scope.PluginScope
import dev.sectile.kernel.plugin.scope.PluginScopeFactory
import dev.sectile.kernel.plugin.scope.provider.DependencyProvider
import dev.sectile.microkernel.plugin.classloading.PluginClassLoader
import dev.sectile.microkernel.plugin.scope.provider.ScopedEventBusProvider

object KernelPluginScopeFactory : PluginScopeFactory {
    private val defaultDependencyProviders: MutableList<DependencyProvider<*>> =
        mutableListOf(
            ScopedEventBusProvider.fromLazy(),
        )

    fun registerDefaultProvider(provider: DependencyProvider<*>) {
        defaultDependencyProviders.add(provider)
    }

    override fun acquire(): PluginScope {
        val classLoader = Thread.currentThread().contextClassLoader
        if (classLoader !is PluginClassLoader) {
            throw IllegalStateException("Attempted to acquire a plugin scope from a non-plugin thread/context")
        }
        return PluginsScopeContainer.getOrCreateScope(classLoader) { create() }
    }

    override fun create(): PluginScope {
        val kernelPluginScope = KernelPluginScope()
        defaultDependencyProviders.forEach { provider -> kernelPluginScope.put(provider) }
        return kernelPluginScope
    }
}
