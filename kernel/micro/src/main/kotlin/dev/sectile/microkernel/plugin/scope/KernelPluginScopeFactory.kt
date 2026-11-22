package dev.sectile.microkernel.plugin.scope

import dev.sectile.kernel.plugin.model.PluginId
import dev.sectile.kernel.plugin.scope.PluginScope
import dev.sectile.kernel.plugin.scope.PluginScopeFactory
import dev.sectile.kernel.plugin.scope.provider.DependencyProvider
import dev.sectile.microkernel.plugin.classloading.PluginClassLoader
import java.util.concurrent.CopyOnWriteArrayList

object KernelPluginScopeFactory : PluginScopeFactory {
    private val defaultDependencyProviders = CopyOnWriteArrayList<() -> DependencyProvider<*>>()

    fun registerDefaultProvider(factory: () -> DependencyProvider<*>) {
        defaultDependencyProviders.add(factory)
    }

    override fun acquire(): PluginScope {
        val classLoader = Thread.currentThread().contextClassLoader
        if (classLoader !is PluginClassLoader) {
            throw IllegalStateException("Attempted to acquire a plugin scope from a non-plugin thread/context")
        }
        return acquire(classLoader)
    }

    override fun acquire(owner: Any): PluginScope {
        val ownerLoader = owner.javaClass.classLoader
        if (ownerLoader is PluginClassLoader) {
            return acquire(ownerLoader)
        }
        return acquire()
    }

    override fun acquire(classLoader: ClassLoader): PluginScope {
        if (classLoader !is PluginClassLoader) {
            throw IllegalStateException("Attempted to acquire a plugin scope from non-plugin classloader: $classLoader")
        }
        if (classLoader.isClosed) {
            throw IllegalStateException("Attempted to acquire a plugin scope from closed plugin classloader: ${classLoader.id}")
        }
        val scope = PluginsScopeContainer.getOrCreateScope(classLoader) { create(classLoader.id) }
        if (classLoader.isClosed) {
            PluginsScopeContainer.removeScope(classLoader)
            if (scope is KernelPluginScope) {
                runCatching { scope.close() }
            }
            throw IllegalStateException("Attempted to acquire a plugin scope from closed plugin classloader: ${classLoader.id}")
        }
        return scope
    }

    internal fun create(pluginId: PluginId? = null): PluginScope {
        val scope = KernelPluginScope(pluginId)
        defaultDependencyProviders.forEach { factory -> scope.put(factory()) }
        return scope
    }
}
