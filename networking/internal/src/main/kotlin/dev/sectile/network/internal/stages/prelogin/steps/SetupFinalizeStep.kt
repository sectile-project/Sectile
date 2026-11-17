package dev.sectile.network.internal.stages.prelogin.steps

import dev.sectile.network.api.player.connection.ConnectionStage
import dev.sectile.network.api.player.stage.StageSteps
import dev.sectile.network.internal.stages.SessionState
import dev.sectile.network.internal.stages.StageContext
import dev.sectile.network.internal.stages.StagePacketStep
import dev.sectile.network.protocol.setup.SetupFinalizePacket

/**
 * Moves the connection to the gameplay stage and sends the state of the world.
 *
 * `SetupFinalize` means the client finished loading and is ready to join the world. The server
 * opens the auxiliary streams (the chunks and the world map) and then sends the state of the
 * world and of the player.
 */
internal object SetupFinalizeStep : StagePacketStep<SetupFinalizePacket> {

    override val name: String = StageSteps.PRE_LOGIN_SETUP_FINALIZE

    override fun run(context: StageContext, packet: SetupFinalizePacket, next: () -> Unit) {
        val state = context.session
        if (state.phase != SessionState.Phase.LOADING) return

        state.phase = SessionState.Phase.PLAY
        context.requestStage(ConnectionStage.POST_LOGIN)

        context.connection.openAuxiliaryStreams().whenComplete { _, error ->
            if (error != null) {
                context.logger.warn(
                    "[${context.connection.getIp()}] Could not open auxiliary streams: ${error.message}",
                )
            }
            context.connection.execute {
                if (context.connection.isOnline()) {
                    WorldStateStep.send(context)
                }
            }
        }

        next()
    }
}
