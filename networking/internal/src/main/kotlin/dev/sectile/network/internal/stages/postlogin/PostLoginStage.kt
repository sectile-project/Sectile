package dev.sectile.network.internal.stages.postlogin

import dev.sectile.network.api.packet.handler.PacketHandlerStorage
import dev.sectile.network.api.packet.handler.on
import dev.sectile.network.api.player.connection.ConnectionStage
import dev.sectile.network.api.player.stage.StageSteps
import dev.sectile.network.internal.stages.Stage
import dev.sectile.network.internal.stages.StageContext
import dev.sectile.network.internal.stages.StageEntryStep
import dev.sectile.network.internal.stages.StageEnvironment
import dev.sectile.network.internal.stages.StagePacketStep
import dev.sectile.network.internal.stages.onClientDisconnect
import dev.sectile.network.internal.stages.onViewRadius
import dev.sectile.network.protocol.ProtocolPacketTable
import dev.sectile.network.protocol.connection.outbound.NetworkTickPacket
import dev.sectile.network.protocol.connection.outbound.PingPacket
import dev.sectile.network.protocol.player.inbound.ClientReadyPacket
import dev.sectile.network.protocol.shared.InstantData
import java.time.Duration
import java.time.Instant

/**
 * POST_LOGIN stage.
 *
 * **Entered when** the client finished loading the world and the server sent the state of the
 * world.
 *
 * **What happens on entry**
 *  - `world-join` - extension point where the engine creates the player and joins a world.
 *    This module only notifies: movement and world state are game logic and live in the engine.
 *
 * **Waits for**
 *  - `ClientReady`: the client is ready for the chunks and/or for the gameplay. The server
 *    confirms the view radius, sends the spawn chunks and starts the tasks that keep the
 *    connection alive.
 *
 * Network liveness (ping and ticks) stays here: it is not game logic.
 *
 * **Exits to** nothing: this is the last stage of the lifecycle.
 */
internal object PostLoginStage : Stage {

    override val stage: ConnectionStage = ConnectionStage.POST_LOGIN

    /** The session stages have no deadline of their own: the play timeout is used. */
    override val timeout: Duration? = null

    override val entrySteps: List<StageEntryStep> = listOf(
        WorldJoinStep,
    )

    override fun register(storage: PacketHandlerStorage, environment: StageEnvironment) {
        val readySteps = listOf(
            ClientReadyStep,
            NetworkTasksStep,
        )

        storage.on(ProtocolPacketTable.CLIENT_READY, ::ClientReadyPacket) { connection, packet ->
            val context = StageContext.of(connection, environment)
            context.runPacketSteps(stage, packet, readySteps)
        }

        storage.onViewRadius()
        storage.onClientDisconnect(environment)
    }
}

/**
 * Notifies the extensions that the connection reached the gameplay stage.
 *
 * This step has no behavior of its own: it exists so the engine has a stable, named point to
 * hook into when a player joins a world.
 */
private object WorldJoinStep : StageEntryStep {

    override val name: String = StageSteps.POST_LOGIN_WORLD_JOIN

    override fun run(
        context: StageContext,
        next: () -> Unit,
    ) = next()
}

/**
 * Marks the session ready when the client reports it.
 *
 * The world view itself — view radius, chunks and world state — is owned by `engine:world` and
 * sent by the POST_LOGIN world hook. This step only records the connection-level flag.
 */
private object ClientReadyStep : StagePacketStep<ClientReadyPacket> {

    override val name: String = StageSteps.POST_LOGIN_CLIENT_READY

    override fun run(
        context: StageContext,
        packet: ClientReadyPacket,
        next: () -> Unit,
    ) {
        context.connection.updateSession {
            ready = packet.readyForGameplay || packet.readyForChunks
        }

        next()
    }
}

/**
 * Starts the tasks that keep the connection alive.
 *
 * The client expects a network tick every second and a ping every few seconds. Both tasks
 * belong to the connection, so they stop with it.
 */
private object NetworkTasksStep : StagePacketStep<ClientReadyPacket> {

    override val name: String = StageSteps.POST_LOGIN_NETWORK_TASKS

    /** Interval between two network ticks. */
    private val TICK_INTERVAL: Duration = Duration.ofSeconds(1)

    /** Interval between two pings. */
    private val PING_INTERVAL: Duration = Duration.ofSeconds(5)

    /** Duration of a tick, in microseconds. */
    private const val TICK_DURATION_MICROS = 50_000

    override fun run(
        context: StageContext,
        packet: ClientReadyPacket,
        next: () -> Unit,
    ) {
        val state = context.session
        if (state.tasksStarted) {
            next()
            return
        }
        state.tasksStarted = true

        val connection = context.connection

        state.tickTask =
            connection.scheduleAtFixedRate(TICK_INTERVAL, TICK_INTERVAL) {
                try {
                    connection.sendPacket(NetworkTickPacket(TICK_DURATION_MICROS, state.serverTick.getAndIncrement()))
                } catch (e: Exception) {
                    context.logger.debug("Failed to send network tick: ${e.javaClass.simpleName}")
                }
            }

        state.pingTask =
            connection.scheduleAtFixedRate(PING_INTERVAL, PING_INTERVAL) {
                try {
                    connection.sendPacket(
                        PingPacket(
                            id = state.pingId.getAndIncrement(),
                            time = InstantData().also { it.seconds = Instant.now().epochSecond },
                        ),
                    )
                } catch (e: Exception) {
                    context.logger.debug("Failed to send ping: ${e.javaClass.simpleName}")
                }
            }

        next()
    }
}
