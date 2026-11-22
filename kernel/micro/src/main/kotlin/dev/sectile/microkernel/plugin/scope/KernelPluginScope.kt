package dev.sectile.microkernel.plugin.scope

import dev.sectile.kernel.plugin.model.PluginId
import dev.sectile.kernel.plugin.scope.PluginScope
import dev.sectile.kernel.plugin.scope.provider.CloseableDependencyProvider
import dev.sectile.kernel.plugin.scope.provider.DependencyProvider
import dev.sectile.kernel.plugin.scope.provider.ProtectedDependencyProvider
import dev.sectile.microkernel.Microkernel
import dev.sectile.microkernel.plugin.classloading.PluginClassLoader

internal class KernelPluginScope(
    private val pluginId: PluginId? = null,
) : PluginScope,
    AutoCloseable {
    private val providers = HashMap<Class<*>, DependencyProvider<*>>()
    private val closeActions = ArrayList<() -> Unit>()

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> get(type: Class<T>): T? = synchronized(providers) {
        providers[type]?.provide() as? T
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> getProvider(type: Class<T>): DependencyProvider<T>? = synchronized(providers) {
        providers[type] as? DependencyProvider<T>
    }

    override fun <T : Any> put(provider: DependencyProvider<T>) {
        synchronized(providers) {
            val type = provider.type
            val oldProvider = providers[type]

            if (oldProvider is ProtectedDependencyProvider) {
                throw IllegalStateException("Cannot replace protected provider of type $type")
            }

            closeProvider(oldProvider)
            providers[type] = provider
        }
    }

    override fun remove(type: Class<*>): DependencyProvider<*>? {
        synchronized(providers) {
            val provider = providers[type]

            if (provider is ProtectedDependencyProvider) {
                throw IllegalStateException("Cannot remove protected provider of type $type")
            }

            return providers.remove(type)
        }
    }

    override fun onClose(action: () -> Unit) {
        synchronized(providers) {
            closeActions.add(action)
        }
    }

    override fun close() {
        val actionsSnapshot: List<() -> Unit>
        val providersSnapshot: List<DependencyProvider<*>>
        synchronized(providers) {
            actionsSnapshot = closeActions.asReversed().toList()
            closeActions.clear()
            providersSnapshot = providers.values.toList()
            providers.clear()
        }

        for (action in actionsSnapshot) {
            runCatching(action).onFailure { logCloseError("close hook", it) }
        }
        providersSnapshot.forEach(::closeProvider)
    }

    private fun closeProvider(provider: DependencyProvider<*>?) {
        if (provider !is CloseableDependencyProvider<*>) {
            return
        }

        runCatching {
            provider.close()
        }.onFailure { throwable ->
            logCloseError("provider", throwable)
        }
    }

    private fun logCloseError(kind: String, throwable: Throwable) {
        val id = pluginId?.toString()
            ?: (Thread.currentThread().contextClassLoader as? PluginClassLoader)?.id?.toString()
            ?: "unknown"
        Microkernel.instance.rootLogger.error("Error while closing $kind in plugin $id", throwable)
    }
}
