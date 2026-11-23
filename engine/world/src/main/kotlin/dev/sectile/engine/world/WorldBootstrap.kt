package dev.sectile.engine.world

import dev.sectile.engine.api.world.World
import dev.sectile.engine.internal.InternalPlayer
import dev.sectile.engine.internal.registry.InternalPlayerRegistry
import dev.sectile.engine.internal.registry.InternalWorldRegistry
import dev.sectile.engine.world.handler.MovementHandler
import dev.sectile.kernel.logger.Logger
import dev.sectile.network.api.packet.handler.PacketHandlerRegistry
import dev.sectile.network.api.player.connection.ConnectionStage
import dev.sectile.network.api.player.stage.StageHookRegistry
import dev.sectile.network.api.player.stage.StageSteps
import dev.sectile.network.api.player.stage.StepDecision
import dev.sectile.network.api.player.stage.StepPoint

object WorldBootstrap {

    /** World used until world selection is implemented. */
    private const val DEFAULT_WORLD_ID = "default"

    /** Reason sent when there is no world to put the player in. */
    private const val NO_WORLD_REASON = "client.general.disconnect.noWorld"

    private var initialized = false

    /**
     * Registers the world, the player creation hook and the gameplay packet handlers.
     *
     * It must be called after the network connector has been initialized.
     */
    @Synchronized
    fun init(
        logger: Logger,
        packetHandlers: PacketHandlerRegistry,
        stageHooks: StageHookRegistry,
    ) {
        if (initialized) return
        initialized = true

        InternalWorldRegistry.register(SimpleWorld(DEFAULT_WORLD_ID, "Default"))

        registerJoinHook(logger, stageHooks)
        packetHandlers.add(MovementHandler)
    }

    /** Creates the player when the connection reaches POST_LOGIN. */
    private fun registerJoinHook(logger: Logger, stageHooks: StageHookRegistry) {
        stageHooks.after(StepPoint(ConnectionStage.POST_LOGIN, StageSteps.POST_LOGIN_WORLD_JOIN)) { ctx ->
            val world: World = InternalWorldRegistry.default ?: return@after StepDecision.Reject(NO_WORLD_REASON)

            val connection = ctx.connection
            val session = connection.session

            val player =
                InternalPlayer(
                    connection = connection,
                    uuid = session.uuid,
                    username = session.username,
                    world = world,
                )

            world.add(player)
            InternalPlayerRegistry.register(player)

            connection.onDisconnect {
                world.remove(player)
                InternalPlayerRegistry.unregister(player)
                logger.info("Player ${player.username} left world ${world.name}")
            }

            // The auxiliary streams are opened here: they belong to the world view, not to the
            // login stages. The world is sent once they are ready.
            connection.openAuxiliaryStreams().whenComplete { _, error ->
                if (error != null) {
                    logger.warn("Could not open auxiliary streams: ${error.message}")
                }
                connection.execute {
                    if (connection.isOnline()) {
                        WorldView.send(player)
                    }
                }
            }

            logger.info("Player ${player.username} joined world ${world.name}")
            StepDecision.Continue
        }
    }
}
