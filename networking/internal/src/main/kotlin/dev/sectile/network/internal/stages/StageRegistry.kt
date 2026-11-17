package dev.sectile.network.internal.stages

import dev.sectile.network.api.packet.handler.PacketHandlerStorage
import dev.sectile.network.api.player.connection.ConnectionStage
import dev.sectile.network.internal.stages.authentication.AuthenticationStage
import dev.sectile.network.internal.stages.handshake.HandshakeStage
import dev.sectile.network.internal.stages.insecureoptions.InsecureOptionsStage
import dev.sectile.network.internal.stages.password.PasswordStage
import dev.sectile.network.internal.stages.postlogin.PostLoginStage
import dev.sectile.network.internal.stages.prelogin.PreLoginStage

/**
 * Registry of every stage of the connection lifecycle.
 *
 * The registry is the single place that knows the whole lifecycle: which stage implements each
 * [ConnectionStage], how long it lasts and what it does when the connection enters it.
 */
internal object StageRegistry {

    /** Every stage of the lifecycle, in the order the connection walks through them. */
    private val stages: List<Stage> = listOf(
        HandshakeStage,
        AuthenticationStage,
        InsecureOptionsStage,
        PasswordStage,
        PreLoginStage,
        PostLoginStage,
    )

    /** Stages indexed by the connection stage they implement. */
    private val byConnectionStage: Map<ConnectionStage, Stage> = stages.associateBy { it.stage }

    /** Registers every stage into the packet handler storage of its connection stage. */
    fun register(storages: Map<ConnectionStage, PacketHandlerStorage>, environment: StageEnvironment) {
        for (stage in stages) {
            stage.register(storages.getValue(stage.stage), environment)
        }
    }

    /** Returns the stage that implements [stage]. */
    fun stageOf(stage: ConnectionStage): Stage = byConnectionStage[stage] ?: error("No stage registered for $stage")

    /** Returns the entry steps of [stage]. */
    fun entryStepsOf(stage: ConnectionStage): List<StageEntryStep> = stageOf(stage).entrySteps
}
