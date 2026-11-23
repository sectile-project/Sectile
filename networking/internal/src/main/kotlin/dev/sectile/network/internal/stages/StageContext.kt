package dev.sectile.network.internal.stages

import dev.sectile.kernel.logger.Logger
import dev.sectile.network.api.packet.InboundPacket
import dev.sectile.network.api.player.ScheduledTask
import dev.sectile.network.api.player.connection.ConnectionCloseCode
import dev.sectile.network.api.player.connection.ConnectionKey
import dev.sectile.network.api.player.connection.ConnectionStage
import dev.sectile.network.api.player.connection.PlayerConnection
import dev.sectile.network.api.player.stage.HookPhase
import dev.sectile.network.api.player.stage.LoginSnapshot
import dev.sectile.network.api.player.stage.StepContext
import dev.sectile.network.api.player.stage.StepDecision
import dev.sectile.network.api.player.stage.StepPoint
import dev.sectile.network.internal.config.NetworkConfig
import dev.sectile.network.internal.player.DisconnectSupport

/**
 * Per connection runtime context shared by every stage and every step.
 *
 * The context bundles everything a stage needs: the connection itself, the server wide
 * [StageEnvironment] and the mutable state that survives from one stage to the next
 * ([login] for the authentication stages and [session] for the world and gameplay stages).
 *
 * It is also where the extensions are applied: before and after every step the hooks
 * registered in [StageHooks] get the chance to reject
 * the connection, to pause it or to move it to another stage.
 */
internal class StageContext(
    val connection: PlayerConnection,
    val environment: StageEnvironment,
) {

    /** Server configuration. */
    val config: NetworkConfig
        get() = environment.config

    /** Server logger. */
    val logger: Logger
        get() = environment.logger

    /** State shared by the login stages (`HANDSHAKE` -> `PASSWORD`). */
    val login: LoginState = LoginState()

    /** State shared by the session stages (`PRE_LOGIN` -> `POST_LOGIN`). */
    val session: SessionState = SessionState()

    /** Deadline task of the stage the connection is currently in. */
    private var stageTimeout: ScheduledTask? = null

    /** Stage the current step asked to move to, committed when the step finishes. */
    private var requestedStage: ConnectionStage? = null

    /** Continuation of a pipeline paused by an extension. */
    @Volatile
    private var heldContinuation: ((StepDecision) -> Unit)? = null

    /** Read-only view of the login state handed to the extensions. */
    private val loginSnapshot: LoginSnapshot = object : LoginSnapshot {
        override val uuid get() = login.uuid
        override val username get() = login.username
        override val identitySubject get() = login.identitySubject
        override val identityToken get() = login.identityToken
        override val accessToken get() = login.accessToken
        override val authenticated get() = config.auth.authenticated
    }

    // ---------------------------------------------------------------------------------------
    // Stage transitions
    // ---------------------------------------------------------------------------------------

    /**
     * Moves the connection to [stage] and runs the entry steps of that stage.
     */
    fun enterStage(stage: ConnectionStage) {
        switchStage(stage)
        runEntrySteps(stage)
    }

    /**
     * Moves the connection to [stage] without running the entry steps of the stage.
     *
     * Switching the stage also swaps the active packet handler storage: from that moment on
     * only the packets accepted by the new stage are decoded, and the packets that do not
     * belong to it are rejected as protocol violations.
     */
    fun switchStage(stage: ConnectionStage) {
        val definition = StageRegistry.stageOf(stage)
        val deadline = definition.timeout

        stageTimeout?.cancel()
        stageTimeout = null

        if (deadline == null) {
            // The stage has no deadline of its own: the transport read timeout is the limit.
            connection.setStage(stage, environment.playTimeout)
            return
        }

        // The transport read timeout stays slightly above the deadline of the login stages, so
        // the "stageTimeout" disconnect is always delivered before the transport gives up on
        // the connection.
        connection.setStage(stage, deadline.plus(definition.readTimeoutGrace))

        stageTimeout = connection.schedule(deadline) {
            if (connection.stage == stage && connection.isOnline()) {
                logger.warn("[${connection.getIp()}] Stage timed out: $stage")
                reject("client.general.disconnect.stageTimeout")
            }
        }
    }

    /**
     * Requests the transition to [stage].
     *
     * The transition is committed when the current step finishes, so the extensions still get
     * the chance to reject the connection or to redirect it somewhere else.
     */
    fun requestStage(stage: ConnectionStage) {
        requestedStage = stage
    }

    /**
     * Runs the entry steps of [stage], one after the other.
     */
    fun runEntrySteps(stage: ConnectionStage) {
        val steps = StageRegistry.entryStepsOf(stage)
        var index = 0

        fun next() {
            if (index >= steps.size) return
            val step = steps[index]
            index++
            runStep(stage, step, { done -> step.run(this, done) }, ::next)
        }

        next()
    }

    /**
     * Runs the steps of [stage] that answer the packet the client sent, one after the other.
     */
    fun <T : InboundPacket> runPacketSteps(
        stage: ConnectionStage,
        packet: T,
        steps: List<StagePacketStep<T>>,
    ) {
        var index = 0

        fun next() {
            if (index >= steps.size) return
            val step = steps[index]
            index++
            runStep(stage, step, { done -> step.run(this, packet, done) }, ::next)
        }

        next()
    }

    /**
     * Runs a step of [stage], giving the extensions the chance to intervene before and after
     * it.
     *
     * @param stage stage the step belongs to.
     * @param step step that runs.
     * @param body runs the step; it receives the continuation invoked when the step succeeds.
     * @param onComplete runs when the step finished and the stage continues.
     */
    private fun runStep(
        stage: ConnectionStage,
        step: StageStep,
        body: (next: () -> Unit) -> Unit,
        onComplete: () -> Unit,
    ) {
        val point = StepPoint(stage, step.name)

        notifyExtensions(point, HookPhase.BEFORE) {
            body {
                // The step succeeded: the transition it asked for is committed now, after the
                // extensions had their chance to intervene.
                val requested = requestedStage
                requestedStage = null

                notifyExtensions(point, HookPhase.AFTER) {
                    if (requested != null) enterStage(requested) else onComplete()
                }
            }
        }
    }

    /**
     * Notifies the extensions about [point] and applies the decision they made.
     *
     * @param point point being notified.
     * @param phase phase the extensions are notified about.
     * @param continuation what to do when the extensions let the pipeline continue.
     */
    private fun notifyExtensions(point: StepPoint, phase: HookPhase, continuation: () -> Unit) {
        val hooks = StageHooks.hooksAt(point, phase) + StageHooks.globalHooks(phase)

        for (hook in hooks) {
            val decision = hook.onStep(HookContextView(point, phase))
            if (decision == StepDecision.Continue) continue

            if (decision is StepDecision.Hold) {
                heldContinuation = { held -> applyDecision(held, point, continuation) }
                return
            }

            applyDecision(decision, point, continuation)
            return
        }

        applyDecision(StepDecision.Continue, point, continuation)
    }

    /**
     * Applies the decision of the extensions.
     *
     * @param decision decision returned by the extensions.
     * @param point point that is being notified.
     * @param continuation what to do when the decision is [StepDecision.Continue].
     */
    private fun applyDecision(decision: StepDecision, point: StepPoint, continuation: () -> Unit) {
        when (decision) {
            StepDecision.Continue -> continuation()

            is StepDecision.Reject -> {
                requestedStage = null
                reject(decision.reason)
            }

            is StepDecision.Redirect -> {
                requestedStage = null
                enterStage(decision.stage)
            }

            is StepDecision.Hold -> heldContinuation = { held -> applyDecision(held, point, continuation) }
        }
    }

    /** Resumes a pipeline that an extension paused. */
    private fun resumeHeld(decision: StepDecision) {
        val held = heldContinuation ?: return
        heldContinuation = null
        held(decision)
    }

    /** Read-only view of the connection handed to the extensions. */
    private inner class HookContextView(
        override val point: StepPoint,
        override val phase: HookPhase,
    ) : StepContext {

        override val connection: PlayerConnection
            get() = this@StageContext.connection

        override val login: LoginSnapshot
            get() = this@StageContext.loginSnapshot

        override fun resume(decision: StepDecision) = resumeHeld(decision)
    }

    /**
     * Rejects the client: the connection is closed with a graceful disconnect.
     *
     * @param reason translation key of the reason shown to the client.
     */
    fun reject(reason: String) {
        connection.disconnect(reason)
    }

    /**
     * Rejects the client with a transport level [code].
     *
     * @param code code the QUIC connection is closed with.
     * @param reason translation key of the reason shown to the client.
     */
    fun reject(code: ConnectionCloseCode, reason: String) {
        logger.info("[${connection.getIp()}] Connection rejected: $reason")
        connection.close(code, DisconnectSupport.reasonEncoder?.invoke(reason))
    }

    companion object {
        /** Key of the context inside the attributes of the connection. */
        private val KEY = ConnectionKey<StageContext>("stage_context")

        /**
         * Returns the context of [connection], creating it the first time it is needed.
         */
        fun of(connection: PlayerConnection, environment: StageEnvironment): StageContext = connection.getOrPutAttribute(KEY) { StageContext(connection, environment) }
    }
}
