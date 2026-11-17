package dev.sectile.kernel.plugin.scope.provider

class LazyProvider<T : Any>(
    override val type: Class<T>,
    supplier: () -> T,
) : DependencyProvider<T> {
    private val lazyValue by lazy(supplier)

    override fun provide(): T = lazyValue
}
