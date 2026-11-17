package dev.sectile.kernel.event

import dev.sectile.kernel.event.handler.ListenerPhase

inline fun <reified T : Any> EventBus.subscribe(
    phase: ListenerPhase = ListenerPhase.DEFAULT,
    priority: Int = 0,
    ignoreCancelled: Boolean = false,
    noinline block: (T) -> Unit,
): Subscription = this.subscribe(T::class.java, ignoreCancelled, priority, phase, block)
