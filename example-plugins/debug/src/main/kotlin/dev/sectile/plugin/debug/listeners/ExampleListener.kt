package dev.sectile.plugin.debug.listeners

import dev.sectile.kernel.event.Listener
import dev.sectile.kernel.event.handler.ListenerPhase
import dev.sectile.kernel.logger.logger
import dev.sectile.kernel.plugin.spi.lifecycle.PluginLifecycle

/** Used to test the package scanner and the listener registrar. */
class ExampleListener(
    private val plugin: PluginLifecycle,
) {

    @Listener(phase = ListenerPhase.MONITOR, priority = 255, ignoreCancelled = true)
    fun exampleListener(test: String) {
        plugin.logger().info("ExampleListener $test")
    }

    @Listener(priority = 1)
    fun exampleListener2(test: String) {
        plugin.logger().info("ExampleListener2 $test")
    }

    @Listener
    fun exampleListener3(test: String) {
        plugin.logger().info("ExampleListener3 $test")
    }
}
