package dev.sectile.kernel.service

interface ServiceRegistration<T : Any> : AutoCloseable {
    val type: Class<T>
    val provider: T
    val priority: Int

    fun unregister()

    override fun close() = unregister()
}

interface ServiceRegistry : ServiceCatalog {

    fun <T : Any> register(
        type: Class<T>,
        provider: T,
        priority: Int = 0,
    ): ServiceRegistration<T>

    fun <T : Any> unregister(
        type: Class<T>,
        provider: T,
    ): Boolean
}

inline fun <reified T : Any> ServiceRegistry.register(
    provider: T,
    priority: Int = 0,
): ServiceRegistration<T> = register(T::class.java, provider, priority)

inline fun <reified T : Any> ServiceRegistry.unregister(provider: T): Boolean = unregister(T::class.java, provider)
