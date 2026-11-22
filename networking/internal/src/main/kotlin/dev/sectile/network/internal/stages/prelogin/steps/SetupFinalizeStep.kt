package dev.sectile.network.internal.stages.prelogin.steps

import dev.sectile.network.api.player.connection.ConnectionStage
import dev.sectile.network.api.player.stage.StageSteps
import dev.sectile.network.internal.stages.SessionState
import dev.sectile.network.internal.stages.StageContext
import dev.sectile.network.internal.stages.StagePacketStep
import dev.sectile.network.protocol.player.inbound.SetupFinalizePacket

/**
 * Moves the connection to the gameplay stage.
 *
 * `SetupFinalize` means the client finished loading and is ready to join the world. From here
 * on the engine owns the player: world assignment, auxiliary streams, chunk streaming and
 * movement are handled by the POST_LOGIN hooks in `engine:world`.
 */
internal object SetupFinalizeStep : StagePacketStep<SetupFinalizePacket> {

    override val name: String = StageSteps.PRE_LOGIN_SETUP_FINALIZE

    override fun run(
        context: StageContext,
        packet: SetupFinalizePacket,
        next: () -> Unit,
    ) {
        val state = context.session
        if (state.phase != SessionState.Phase.LOADING) return

        state.phase = SessionState.Phase.PLAY
        context.requestStage(ConnectionStage.POST_LOGIN)

        next()
    }
}
