package dev.sectile.network.internal.stages.prelogin.steps

import dev.sectile.network.api.player.stage.StageSteps
import dev.sectile.network.internal.stages.StageContext
import dev.sectile.network.internal.stages.StageEntryStep

/**
 * Starts the session of the player.
 *
 * The session is marked as started, so it is only started once, and the tasks that keep the
 * connection alive are stopped when the player leaves.
 */
internal object SessionStartStep : StageEntryStep {

    override val name: String = StageSteps.PRE_LOGIN_SESSION_START

    override fun run(context: StageContext, next: () -> Unit) {
        val state = context.session
        if (!state.started.compareAndSet(false, true)) {
            next()
            return
        }

        context.connection.onDisconnect {
            state.tickTask?.cancel()
            state.pingTask?.cancel()
            state.tickTask = null
            state.pingTask = null
        }

        val session = context.connection.session
        context.logger.info(
            "[${context.connection.getIp()}] Starting session for ${session.username} (${session.uuid})",
        )

        next()
    }
}
