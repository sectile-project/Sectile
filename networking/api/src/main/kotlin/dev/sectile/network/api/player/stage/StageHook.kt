package dev.sectile.network.api.player.stage

/**
 * An extension of the stage machine.
 *
 * A hook is notified before and after every step of the lifecycle and decides what happens
 * next: continue, close the connection, move it to another stage or pause the pipeline.
 */
fun interface StageHook {

    /**
     * Called on the connection event loop.
     *
     * @param context read-only view of the connection and of the step being notified.
     * @return the decision to apply.
     */
    fun onStep(context: StepContext): StepDecision
}
