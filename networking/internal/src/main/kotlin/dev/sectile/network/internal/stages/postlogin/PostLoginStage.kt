package dev.sectile.network.internal.stages.postlogin

import dev.sectile.network.api.buffer.ReadBuffer
import dev.sectile.network.api.packet.handler.PacketHandlerStorage
import dev.sectile.network.api.packet.handler.handle
import dev.sectile.network.api.packet.handler.on
import dev.sectile.network.api.player.connection.ConnectionStage
import dev.sectile.network.api.player.stage.StageSteps
import dev.sectile.network.internal.stages.SpawnChunks
import dev.sectile.network.internal.stages.Stage
import dev.sectile.network.internal.stages.StageContext
import dev.sectile.network.internal.stages.StageEnvironment
import dev.sectile.network.internal.stages.StagePacketStep
import dev.sectile.network.internal.stages.onClientDisconnect
import dev.sectile.network.internal.stages.onViewRadius
import dev.sectile.network.protocol.ProtocolPacketTable
import dev.sectile.network.protocol.connection.NetworkTickPacket
import dev.sectile.network.protocol.connection.PingPacket
import dev.sectile.network.protocol.player.ClientReadyPacket
import dev.sectile.network.protocol.setup.ViewRadiusPacket
import dev.sectile.network.protocol.structures.InstantData
import java.time.Duration
import java.time.Instant

/**
 * POST_LOGIN stage.
 *
 * **Entered when** the client finished loading the world and the server sent the state of the
 * world.
 *
 * **Waits for**
 *  - `ClientReady`: the client is ready for the chunks and/or for the gameplay. The server
 *    confirms the view radius, sends the spawn chunks and starts the tasks that keep the
 *    connection alive.
 *  - `ClientMovement`: the position and the look of the player, which are kept in the session
 *    state.
 *
 * **Exits to** nothing: this is the last stage of the lifecycle.
 */
internal object PostLoginStage : Stage {

    override val stage: ConnectionStage = ConnectionStage.POST_LOGIN

    /** The session stages have no deadline of their own: the play timeout is used. */
    override val timeout: Duration? = null

    override fun register(storage: PacketHandlerStorage, environment: StageEnvironment) {
        val readySteps = listOf<StagePacketStep<ClientReadyPacket>>(
            ClientReadyStep,
            NetworkTasksStep,
        )

        storage.on(ProtocolPacketTable.CLIENT_READY, ::ClientReadyPacket) { connection, packet ->
            val context = StageContext.of(connection, environment)
            context.runPacketSteps(stage, packet, readySteps)
        }

        storage.handle(ProtocolPacketTable.CLIENT_MOVEMENT) { connection, buffer ->
            val context = StageContext.of(connection, environment)
            readMovement(context, buffer)
        }

        storage.onViewRadius()
        storage.onClientDisconnect(environment)
    }

    /**
     * Reads the position and the look of the player.
     *
     * The movement packet is read directly from the buffer: the payload is a fixed structure,
     * and decoding it by hand avoids allocating an intermediate object for every movement.
     */
    private fun readMovement(context: StageContext, buffer: ReadBuffer) {
        val base = buffer.index
        val nullBits0 = buffer.readUnsignedByte()
        val hasPos = (nullBits0 and 0x04) != 0
        val hasLook = (nullBits0 and 0x10) != 0
        if (!hasPos && !hasLook) return

        var x = 0.0
        var y = 0.0
        var z = 0.0
        if (hasPos) {
            buffer.skipTo(base + MOVEMENT_ABS_POS_OFFSET)
            x = buffer.readDouble()
            y = buffer.readDouble()
            z = buffer.readDouble()
        }

        var yaw = 0f
        var pitch = 0f
        if (hasLook) {
            buffer.skipTo(base + MOVEMENT_LOOK_OFFSET)
            yaw = buffer.readFloat()
            pitch = buffer.readFloat()
        }

        context.connection.updateSession {
            if (hasPos) {
                positionX = x
                positionY = y
                positionZ = z
            }
            if (hasLook) {
                this.yaw = yaw
                this.pitch = pitch
            }
        }
    }

    /** Offset of the absolute position inside the movement payload. */
    private const val MOVEMENT_ABS_POS_OFFSET = 12

    /** Offset of the look angles inside the movement payload. */
    private const val MOVEMENT_LOOK_OFFSET = 48
}

/**
 * Answers the `ClientReady` packet of the client.
 *
 * A client that is ready for the chunks but not for the gameplay still needs the view radius
 * and the chunks around the spawn point. The session is marked as ready, so the server knows
 * the player is in the world.
 */
private object ClientReadyStep : StagePacketStep<ClientReadyPacket> {

    override val name: String = StageSteps.POST_LOGIN_CLIENT_READY

    /** Radius of the view, in blocks. */
    private const val VIEW_RADIUS_BLOCKS = 1024

    override fun run(context: StageContext, packet: ClientReadyPacket, next: () -> Unit) {
        val connection = context.connection

        if (packet.readyForChunks && !packet.readyForGameplay) {
            connection.sendPacket(ViewRadiusPacket(VIEW_RADIUS_BLOCKS))
            SpawnChunks.send(context)
        }

        connection.updateSession {
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

    override fun run(context: StageContext, packet: ClientReadyPacket, next: () -> Unit) {
        val state = context.session
        if (state.tasksStarted) {
            next()
            return
        }
        state.tasksStarted = true

        val connection = context.connection

        state.tickTask = connection.scheduleAtFixedRate(TICK_INTERVAL, TICK_INTERVAL) {
            try {
                connection.sendPacket(NetworkTickPacket(TICK_DURATION_MICROS, state.serverTick.getAndIncrement()))
            } catch (e: Exception) {
                context.logger.debug("Failed to send network tick: ${e.javaClass.simpleName}")
            }
        }

        state.pingTask = connection.scheduleAtFixedRate(PING_INTERVAL, PING_INTERVAL) {
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
