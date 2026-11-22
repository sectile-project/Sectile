package dev.sectile.microkernel.plugin.scope.provider

import dev.sectile.kernel.event.EventBus
import dev.sectile.kernel.event.Subscription
import dev.sectile.kernel.event.handler.EventHandler
import dev.sectile.kernel.event.handler.ListenerPhase
import dev.sectile.kernel.plugin.scope.provider.CloseableDependencyProvider
import dev.sectile.kernel.plugin.scope.provider.ProtectedDependencyProvider
import dev.sectile.kernel.plugin.scope.provider.ProtectedLazyProvider
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean

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

    private fun wrapSubscription(subscription: Subscription): Subscription {
        if (subscription.handlers.isEmpty()) {
            return subscription
        }
        val scoped = ScopedSubscription(subscription)
        subscriptions.add(scoped)
        return scoped
    }

    override fun subscribe(listener: Any): Subscription = wrapSubscription(delegate.subscribe(listener))

    override fun <T : Any> subscribe(
        eventClass: Class<T>,
        ignoreCancelled: Boolean,
        priority: Int,
        phase: ListenerPhase,
        block: (T) -> Unit,
    ): Subscription = wrapSubscription(delegate.subscribe(eventClass, ignoreCancelled, priority, phase, block))

    override fun subscribe(handler: EventHandler): Subscription = wrapSubscription(delegate.subscribe(handler))

    override fun unsubscribe(subscription: Subscription) {
        val removed = synchronized(subscriptions) {
            subscriptions.remove(subscription)
        }
        if (removed) {
            subscription.unsubscribe()
        }
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

    private inner class ScopedSubscription(
        private val raw: Subscription,
    ) : Subscription {
        private val unsubscribed = AtomicBoolean(false)

        override val handlers: Collection<EventHandler>
            get() = raw.handlers

        override fun unsubscribe() {
            synchronized(subscriptions) {
                subscriptions.remove(this)
            }
            if (unsubscribed.compareAndSet(false, true)) {
                raw.unsubscribe()
            }
        }
    }
}
