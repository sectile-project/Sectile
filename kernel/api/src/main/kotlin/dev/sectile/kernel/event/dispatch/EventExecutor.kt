package dev.sectile.kernel.event.dispatch

interface EventExecutor {
    fun execute(event: Any)
}
