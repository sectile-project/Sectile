package dev.sectile.plugin.debug

import dev.sectile.kernel.event.handler.ListenerPhase
import dev.sectile.kernel.event.subscribe
import dev.sectile.kernel.logger.logger
import dev.sectile.kernel.plugin.spi.lifecycle.BasePluginLifecycle
import dev.sectile.sdk.extension.eventbus
import dev.sectile.sdk.extension.scan
import dev.sectile.sdk.extension.scope
import dev.sectile.sdk.plugin.annotation.Plugin

@Plugin(id = "sectiledebug", version = "1.0.0-ALPHA")
class DebugPlugin : BasePluginLifecycle() {

    private val logger = this.logger()

    override fun load() {
        logger.info("Loaded!")
    }

    override fun enable() {
        logger.info("Enabled!")

        // Listeners registered through scope() are unsubscribed automatically.
        val eventbus = scope.eventbus()

        eventbus.subscribe<String>(
            phase = ListenerPhase.MONITOR,
            priority = 255,
        ) {
            logger.info("Received $it")
        }

        scope.scan {
            provideWithHierarchy(this@DebugPlugin)
            listeners() // Uses the plugin's main package + ".listeners"
            // Same as: listeners("dev.sectile.plugin.debug.listeners")
        }

        eventbus.publish("test")

        val config = loadConfig()
        logger.info("Example config: test-string: ${config.testString} - duration: ${config.exampleDuration}")
    }
}
