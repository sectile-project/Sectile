package dev.sectile.microkernel.plugin.scope.provider

import dev.sectile.kernel.plugin.scope.provider.CloseableDependencyProvider
import dev.sectile.kernel.plugin.scope.provider.ProtectedDependencyProvider
import java.util.concurrent.ExecutorService

internal class ScopedExecutorProvider(
    override val type: Class<ExecutorService> = ExecutorService::class.java,
    supplier: () -> ExecutorService,
) : CloseableDependencyProvider<ExecutorService>,
    ProtectedDependencyProvider {

    private val lazyDelegate = lazy(supplier)

    override fun provide(): ExecutorService = lazyDelegate.value

    override fun close() {
        if (lazyDelegate.isInitialized()) {
            lazyDelegate.value.shutdownNow()
        }
    }
}
