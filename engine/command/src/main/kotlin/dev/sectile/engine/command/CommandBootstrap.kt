package dev.sectile.engine.command

import dev.sectile.engine.api.Engine
import dev.sectile.kernel.logger.Logger

/**
 * Connects the command registry to the Hytale command packet handlers.
 */
object CommandBootstrap {

    @Volatile
    private var networkInitialized = false

    fun initNetwork(logger: Logger) {
        if (networkInitialized) return
        networkInitialized = true

        CommandPacketHandler.register(Engine.commands)
        logger.debug("Registered command packet handlers.")
    }
}
