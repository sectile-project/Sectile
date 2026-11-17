package dev.sectile.network.api.player.stage

import dev.sectile.network.api.player.connection.ConnectionStage

/**
 * A point of the stage machine where extensions can hook in.
 *
 * A point is the combination of a stage and the name of one of its steps. The canonical names
 * are listed in [StageSteps].
 *
 * @property stage stage the step belongs to.
 * @property step name of the step.
 */
data class StepPoint(
    val stage: ConnectionStage,
    val step: String,
) {

    override fun toString(): String = "$stage/$step"
}
