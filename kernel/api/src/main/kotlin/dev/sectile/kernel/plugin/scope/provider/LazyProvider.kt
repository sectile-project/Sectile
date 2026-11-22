package dev.sectile.kernel.plugin.scope.provider

open class LazyProvider<T : Any>(
    override val type: Class<T>,
    supplier: () -> T,
) : CloseableDependencyProvider<T> {
    private val lazyDelegate = lazy(supplier)

    override fun provide(): T = lazyDelegate.value

    override fun close() {
        if (!lazyDelegate.isInitialized()) return
        when (val value = lazyDelegate.value) {
            is CloseableDependencyProvider<*> -> value.close()
            is AutoCloseable -> value.close()
        }
    }
}

class ProtectedLazyProvider<T : Any>(
    type: Class<T>,
    supplier: () -> T,
) : LazyProvider<T>(type, supplier),
    ProtectedDependencyProvider
