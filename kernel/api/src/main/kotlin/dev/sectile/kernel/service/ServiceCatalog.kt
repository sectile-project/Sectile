package dev.sectile.kernel.service

interface ServiceCatalog {

    operator fun <T : Any> get(type: Class<T>): T?

    fun <T : Any> all(type: Class<T>): List<T>

    fun contains(type: Class<*>): Boolean
}

inline fun <reified T : Any> ServiceCatalog.get(): T? = get(T::class.java)

inline fun <reified T : Any> ServiceCatalog.all(): List<T> = all(T::class.java)

inline fun <reified T : Any> ServiceCatalog.contains(): Boolean = contains(T::class.java)
