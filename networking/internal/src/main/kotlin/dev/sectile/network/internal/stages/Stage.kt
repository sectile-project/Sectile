package dev.sectile.network.internal.stages

import dev.sectile.network.api.packet.InboundPacket
import dev.sectile.network.api.packet.handler.PacketHandlerStorage
import dev.sectile.network.api.player.connection.ConnectionStage
import java.time.Duration

/**
 * A stage of the connection lifecycle.
 *
 * A stage is a small state machine: it knows which packets it waits for, how long the client
 * has and what it does when the connection enters it. Its work is split into [StageStep]s, so
 * the whole process of a stage can be read from top to bottom.
 *
 * The stages of the lifecycle are:
 *
 * ```
 *  HANDSHAKE ──┬──▶ INSECURE_OPTIONS ──┐         (insecure mode)
 *              │                        ├──▶ PASSWORD ──▶ PRE_LOGIN ──▶ POST_LOGIN
 *              └──▶ AUTHENTICATION ─────┘
 * ```
 */
internal interface Stage {

    /** The connection stage implemented by this machine. */
    val stage: ConnectionStage

    /**
     * How long the client has to complete this stage.
     *
     * The login stages have a fixed deadline: the connection is closed with
     * `client.general.disconnect.stageTimeout` when it is reached. The stages without a deadline
     * (the handshake and the session stages) rely on the transport read timeout instead, which
     * is refreshed with every packet the client sends.
     */
    val timeout: Duration?

    /**
     * Extra time added to the transport read timeout.
     *
     * The login stages add a few seconds so the `stageTimeout` disconnect is always delivered
     * before the transport closes the connection on its own.
     */
    val readTimeoutGrace: Duration
        get() = Duration.ZERO

    /** Steps executed when the connection enters this stage. */
    val entrySteps: List<StageEntryStep>
        get() = emptyList()

    /** Registers the handlers of the packets this stage waits for. */
    fun register(storage: PacketHandlerStorage, environment: StageEnvironment)
}

/**
 * A step of a [Stage].
 *
 * A step is the smallest unit of work of the stage machine. Its name is the canonical
 * identifier of the extension point it represents: plugins hook on it through
 * [StageHooks].
 */
internal interface StageStep {

    /** Canonical name of the step, listed in [dev.sectile.network.api.player.stage.StageSteps]. */
    val name: String
}

/**
 * A step that runs when the connection enters the stage that owns it.
 */
internal interface StageEntryStep : StageStep {

    /**
     * Runs the step.
     *
     * @param context runtime context of the connection.
     * @param next continues with the next step of the stage. It must be invoked exactly once
     *   when the step succeeds; a step that rejects the client must not invoke it.
     */
    fun run(context: StageContext, next: () -> Unit)
}

/**
 * A step that runs when the client sends the packet the stage waits for.
 */
internal interface StagePacketStep<T : InboundPacket> : StageStep {

    /**
     * Runs the step with the packet received from the client.
     *
     * @param context runtime context of the connection.
     * @param packet packet received from the client.
     * @param next continues with the next step of the stage. It must be invoked exactly once
     *   when the step succeeds; a step that rejects the client must not invoke it.
     */
    fun run(context: StageContext, packet: T, next: () -> Unit)
}
