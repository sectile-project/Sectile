package dev.sectile.microkernel.plugin.scope.provider

import dev.sectile.kernel.Kernel
import dev.sectile.kernel.event.EventBus
import dev.sectile.kernel.event.Subscription
import dev.sectile.kernel.event.handler.EventHandler
import dev.sectile.kernel.event.handler.ListenerPhase
import dev.sectile.kernel.plugin.scope.provider.CloseableDependencyProvider
import dev.sectile.kernel.plugin.scope.provider.LazyProvider
import dev.sectile.kernel.plugin.scope.provider.ProtectedDependencyProvider
import java.util.*

class ScopedEventBusProvider(
    private val delegate: EventBus,
    val subscriptions: MutableList<Subscription> = Collections.synchronizedList(mutableListOf()),

    override val type: Class<EventBus> = EventBus::class.java,
) : EventBus,
    CloseableDependencyProvider<EventBus>,
    ProtectedDependencyProvider {

    companion object {
        fun fromLazy(): LazyProvider<EventBus> {
            val supplier: () -> EventBus = { ScopedEventBusProvider(Kernel.bus) }

            return LazyProvider(
                type = EventBus::class.java,
                supplier = supplier,
            )
        }
    }

    private fun addSubscription(subscription: Subscription) {
        if (!subscription.handlers.isEmpty()) {
            this.subscriptions.add(subscription)
        }
    }

    override fun subscribe(listener: Any): Subscription {
        val subscription = delegate.subscribe(listener)
        addSubscription(subscription)
        return subscription
    }

    override fun <T : Any> subscribe(
        eventClass: Class<T>,
        ignoreCancelled: Boolean,
        priority: Int,
        phase: ListenerPhase,
        block: (T) -> Unit,
    ): Subscription {
        val subscription = delegate.subscribe(eventClass, ignoreCancelled, priority, phase, block)
        addSubscription(subscription)
        return subscription
    }

    override fun subscribe(handler: EventHandler): Subscription {
        val subscription = delegate.subscribe(handler)
        addSubscription(subscription)
        return subscription
    }

    override fun unsubscribe(subscription: Subscription) {
        subscription.unsubscribe()
        subscriptions.remove(subscription)
    }

    override fun publish(event: Any) {
        delegate.publish(event)
    }

    override fun clear() {
        for (subscription in subscriptions) {
            subscription.unsubscribe()
        }
    }

    override fun provide(): EventBus = this

    override fun close() {
        this.clear()
    }
}
