package dev.sectile.kernel.event

interface EventDispatcher {
    fun publish(event: Any)
}
