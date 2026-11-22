package dev.sectile.microkernel.plugin.scope.provider

import dev.sectile.kernel.event.EventBus
import dev.sectile.kernel.event.Subscription
import dev.sectile.kernel.event.handler.EventHandler
import dev.sectile.kernel.event.handler.ListenerPhase
import dev.sectile.kernel.plugin.scope.provider.CloseableDependencyProvider
import dev.sectile.kernel.plugin.scope.provider.ProtectedDependencyProvider
import dev.sectile.kernel.plugin.scope.provider.ProtectedLazyProvider
import java.util.Collections

class ScopedEventBusProvider(
    private val delegate: EventBus,
    val subscriptions: MutableList<Subscription> = Collections.synchronizedList(mutableListOf()),
    override val type: Class<EventBus> = EventBus::class.java,
) : EventBus,
    CloseableDependencyProvider<EventBus>,
    ProtectedDependencyProvider {

    companion object {
        fun fromLazy(delegate: EventBus): ProtectedLazyProvider<EventBus> = ProtectedLazyProvider(EventBus::class.java) { ScopedEventBusProvider(delegate) }
    }

    private fun addSubscription(subscription: Subscription) {
        if (subscription.handlers.isNotEmpty()) {
            subscriptions.add(subscription)
        }
    }

    override fun subscribe(listener: Any): Subscription = delegate.subscribe(listener).also(::addSubscription)

    override fun <T : Any> subscribe(
        eventClass: Class<T>,
        ignoreCancelled: Boolean,
        priority: Int,
        phase: ListenerPhase,
        block: (T) -> Unit,
    ): Subscription = delegate.subscribe(eventClass, ignoreCancelled, priority, phase, block).also(::addSubscription)

    override fun subscribe(handler: EventHandler): Subscription = delegate.subscribe(handler).also(::addSubscription)

    override fun unsubscribe(subscription: Subscription) {
        subscription.unsubscribe()
        subscriptions.remove(subscription)
    }

    override fun publish(event: Any) {
        delegate.publish(event)
    }

    fun clear() {
        val snapshot = synchronized(subscriptions) {
            subscriptions.toList().also { subscriptions.clear() }
        }
        snapshot.forEach { it.unsubscribe() }
    }

    override fun provide(): EventBus = this

    override fun close() {
        clear()
    }
}
