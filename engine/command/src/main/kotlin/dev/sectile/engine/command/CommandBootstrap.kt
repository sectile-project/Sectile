package dev.sectile.engine.command

import dev.sectile.engine.api.Engine
import dev.sectile.kernel.logger.Logger
import dev.sectile.network.api.packet.handler.PacketHandlerRegistry
import dev.sectile.network.api.player.stage.StageHookRegistry

/**
 * Connects the command registry to the Hytale command packet handlers.
 */
object CommandBootstrap {

    @Volatile
    private var networkInitialized = false

    fun initNetwork(
        logger: Logger,
        packetHandlers: PacketHandlerRegistry,
        stageHooks: StageHookRegistry,
    ) {
        if (networkInitialized) return
        networkInitialized = true

        CommandPacketHandler.register(Engine.commands, packetHandlers, stageHooks)
        logger.debug("Registered command packet handlers.")
    }
}
