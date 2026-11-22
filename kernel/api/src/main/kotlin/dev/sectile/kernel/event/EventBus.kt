package dev.sectile.kernel.event

import dev.sectile.kernel.event.handler.EventHandler
import dev.sectile.kernel.event.handler.ListenerPhase

interface EventBus : EventDispatcher {
    fun subscribe(listener: Any): Subscription

    fun <T : Any> subscribe(
        eventClass: Class<T>,
        ignoreCancelled: Boolean = false,
        priority: Int = 0,
        phase: ListenerPhase = ListenerPhase.DEFAULT,
        block: (T) -> Unit,
    ): Subscription

    fun subscribe(handler: EventHandler): Subscription

    fun unsubscribe(subscription: Subscription)
}
