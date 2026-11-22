package dev.sectile.microkernel.service

import dev.sectile.kernel.service.ServiceRegistration
import dev.sectile.kernel.service.ServiceRegistry
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

internal class DefaultServiceRegistry : ServiceRegistry {

    private val buckets = ConcurrentHashMap<Class<*>, ServiceBucket>()

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> get(type: Class<T>): T? = buckets[type]?.entries?.firstOrNull()?.provider as? T

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> all(type: Class<T>): List<T> = buckets[type]?.entries?.map { it.provider as T } ?: emptyList()

    override fun contains(type: Class<*>): Boolean = !buckets[type]?.entries.isNullOrEmpty()

    override fun <T : Any> register(
        type: Class<T>,
        provider: T,
        priority: Int,
    ): ServiceRegistration<T> {
        require(type.isInstance(provider)) {
            "Provider ${provider.javaClass.name} does not implement ${type.name}"
        }
        val entry = Entry(type, provider, priority)
        buckets.compute(type) { _, bucket ->
            val target = bucket ?: ServiceBucket()
            target.add(entry)
            target
        }
        val unregistered = AtomicBoolean(false)
        return object : ServiceRegistration<T> {
            override val type: Class<T> get() = entry.type
            override val provider: T get() = entry.provider
            override val priority: Int get() = entry.priority

            override fun unregister() {
                if (!unregistered.compareAndSet(false, true)) return
                buckets.computeIfPresent(type) { _, bucket ->
                    bucket.removeEntry(entry)
                    if (bucket.entries.isEmpty()) null else bucket
                }
            }
        }
    }

    override fun <T : Any> unregister(
        type: Class<T>,
        provider: T,
    ): Boolean {
        var removed = false
        buckets.computeIfPresent(type) { _, bucket ->
            removed = bucket.removeProvider(provider)
            if (bucket.entries.isEmpty()) null else bucket
        }
        return removed
    }

    private class Entry<T : Any>(
        val type: Class<T>,
        val provider: T,
        val priority: Int,
    )

    private class ServiceBucket {
        @Volatile
        var entries: List<Entry<*>> = emptyList()

        fun add(entry: Entry<*>) {
            entries = (entries + entry).sortedByDescending { it.priority }
        }

        fun removeEntry(entry: Entry<*>) {
            entries = entries.filterNot { it === entry }
        }

        fun removeProvider(provider: Any): Boolean {
            val index = entries.indexOfLast { it.provider == provider }
            if (index < 0) return false
            entries = entries.filterIndexed { i, _ -> i != index }
            return true
        }
    }
}
