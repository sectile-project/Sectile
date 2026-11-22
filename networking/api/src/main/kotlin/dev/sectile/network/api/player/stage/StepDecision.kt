package dev.sectile.network.api.player.stage

import dev.sectile.network.api.player.connection.ConnectionStage

/**
 * What an extension decided after being notified about a step of the stage machine.
 */
sealed interface StepDecision {

    /**
     * The extension has nothing to say: the stage continues with the next step.
     */
    data object Continue : StepDecision

    /**
     * The connection is closed with [reason].
     *
     * The server sends the disconnect packet to the client and notifies
     */
    data class Reject(val reason: String) : StepDecision

    /**
     * The connection moves to [stage] instead of continuing with the current stage.
     *
     * The entry steps of [stage] run as if the connection had entered it normally.
     */
    data class Redirect(val stage: ConnectionStage) : StepDecision

    /**
     * The pipeline is paused until the extension resumes it.
     *
     * Use it for checks that need to wait, like an HTTP call to an antibot service. The
     * extension must call [StepContext.resume] with the final decision; resuming
     * synchronously, before the hook returns, has no effect.
     */
    data object Hold : StepDecision
}
