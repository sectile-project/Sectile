package dev.sectile.network.internal.stages

import dev.sectile.network.api.player.connection.ConnectionStage
import dev.sectile.network.api.player.stage.HookPhase
import dev.sectile.network.api.player.stage.StageHooks
import dev.sectile.network.api.player.stage.StepContext
import dev.sectile.network.api.player.stage.StepDecision
import dev.sectile.network.api.player.stage.StepPoint
import dev.sectile.network.internal.auth.TestLogger
import dev.sectile.network.internal.config.AuthConfig
import dev.sectile.network.internal.config.AuthMode
import dev.sectile.network.internal.config.NetworkConfig
import dev.sectile.network.protocol.connection.inbound.ConnectPacket
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Tests of the extension points of the stage machine.
 *
 * They cover what a plugin can do from the outside: be notified around a step, close the
 * connection, move it to another stage or pause the pipeline.
 */
class StageExtensionsTest {

    private lateinit var connection: FakePlayerConnection
    private lateinit var context: StageContext

    @BeforeEach
    fun setUp() {
        StageHooks.clear()
        connection = FakePlayerConnection()
        context = StageContext(connection, environment())
    }

    @AfterEach
    fun tearDown() {
        StageHooks.clear()
    }

    // ---------------------------------------------------------------------------------------
    // Notification
    // ---------------------------------------------------------------------------------------

    @Test
    fun `a hook is notified before and after the step`() {
        val phases = mutableListOf<HookPhase>()
        val points = mutableListOf<StepPoint>()

        StageHooks.before(StepPoint(ConnectionStage.HANDSHAKE, "first")) { ctx ->
            phases += ctx.phase
            points += ctx.point
            StepDecision.Continue
        }
        StageHooks.after(StepPoint(ConnectionStage.HANDSHAKE, "first")) { ctx ->
            phases += ctx.phase
            points += ctx.point
            StepDecision.Continue
        }

        context.runPacketSteps(ConnectionStage.HANDSHAKE, ConnectPacket(), listOf(RecordingStep("first")))

        assertEquals(listOf(HookPhase.BEFORE, HookPhase.AFTER), phases)
        assertEquals(StepPoint(ConnectionStage.HANDSHAKE, "first"), points.first())
    }

    @Test
    fun `a global hook runs on every step`() {
        val steps = mutableListOf<String>()

        StageHooks.afterEveryStep { ctx ->
            steps += ctx.point.step
            StepDecision.Continue
        }

        context.runPacketSteps(
            ConnectionStage.HANDSHAKE,
            ConnectPacket(),
            listOf(RecordingStep("first"), RecordingStep("second")),
        )

        assertEquals(listOf("first", "second"), steps)
    }

    @Test
    fun `the hook sees the identity resolved so far`() {
        var snapshot: String? = null

        StageHooks.after(StepPoint(ConnectionStage.AUTHENTICATION, "token")) { ctx ->
            snapshot = ctx.login?.username
            StepDecision.Continue
        }

        context.login.username = "sectile"
        context.runPacketSteps(ConnectionStage.AUTHENTICATION, ConnectPacket(), listOf(RecordingStep("token")))

        assertEquals("sectile", snapshot)
    }

    // ---------------------------------------------------------------------------------------
    // Decisions
    // ---------------------------------------------------------------------------------------

    @Test
    fun `a reject closes the connection and stops the stage`() {
        val reached = mutableListOf<String>()

        StageHooks.after(StepPoint(ConnectionStage.HANDSHAKE, "first")) {
            StepDecision.Reject("client.general.disconnect.banned")
        }

        context.runPacketSteps(
            ConnectionStage.HANDSHAKE,
            ConnectPacket(),
            listOf(RecordingStep("first"), RecordingStep("second") { reached += "second" }),
        )

        assertEquals("client.general.disconnect.banned", connection.disconnectReason)
        assertEquals(listOf<String>(), reached)
    }

    @Test
    fun `a redirect moves the connection to another stage`() {
        StageHooks.after(StepPoint(ConnectionStage.HANDSHAKE, "first")) {
            StepDecision.Redirect(ConnectionStage.PRE_LOGIN)
        }

        context.runPacketSteps(ConnectionStage.HANDSHAKE, ConnectPacket(), listOf(RecordingStep("first")))

        assertEquals(ConnectionStage.PRE_LOGIN, connection.stage)
    }

    @Test
    fun `a hold pauses the pipeline until it is resumed`() {
        val reached = mutableListOf<String>()
        var held: StepContext? = null

        StageHooks.after(StepPoint(ConnectionStage.HANDSHAKE, "first")) { ctx ->
            held = ctx
            StepDecision.Hold
        }

        context.runPacketSteps(
            ConnectionStage.HANDSHAKE,
            ConnectPacket(),
            listOf(RecordingStep("first"), RecordingStep("second") { reached += "second" }),
        )

        // The pipeline is paused: the second step did not run yet.
        assertEquals(listOf<String>(), reached)

        held!!.resume(StepDecision.Continue)

        assertEquals(listOf("second"), reached)
    }

    @Test
    fun `a resumed reject closes the connection`() {
        var held: StepContext? = null

        StageHooks.after(StepPoint(ConnectionStage.HANDSHAKE, "first")) { ctx ->
            held = ctx
            StepDecision.Hold
        }

        context.runPacketSteps(ConnectionStage.HANDSHAKE, ConnectPacket(), listOf(RecordingStep("first")))

        held!!.resume(StepDecision.Reject("client.general.disconnect.banned"))

        assertEquals("client.general.disconnect.banned", connection.disconnectReason)
    }

    @Test
    fun `a before hook stops the step from running`() {
        val reached = mutableListOf<String>()

        StageHooks.before(StepPoint(ConnectionStage.HANDSHAKE, "first")) {
            StepDecision.Reject("client.general.disconnect.banned")
        }

        context.runPacketSteps(
            ConnectionStage.HANDSHAKE,
            ConnectPacket(),
            listOf(RecordingStep("first") { reached += "first" }),
        )

        assertEquals(listOf<String>(), reached)
        assertEquals("client.general.disconnect.banned", connection.disconnectReason)
    }

    // ---------------------------------------------------------------------------------------
    // Transitions
    // ---------------------------------------------------------------------------------------

    @Test
    fun `a requested transition is committed when the step finishes`() {
        val steps = listOf(
            RequestingStep("first", ConnectionStage.PASSWORD),
            RecordingStep("second"),
        )

        context.runPacketSteps(ConnectionStage.HANDSHAKE, ConnectPacket(), steps)

        // The transition is committed after the step, so the second step of the same stage
        // never runs: the connection already moved to the new stage.
        assertEquals(ConnectionStage.PASSWORD, connection.stage)
    }

    @Test
    fun `the extensions can veto a requested transition`() {
        StageHooks.after(StepPoint(ConnectionStage.HANDSHAKE, "first")) {
            StepDecision.Reject("client.general.disconnect.banned")
        }

        val steps = listOf(RequestingStep("first", ConnectionStage.PASSWORD))

        context.runPacketSteps(ConnectionStage.HANDSHAKE, ConnectPacket(), steps)

        assertEquals(ConnectionStage.HANDSHAKE, connection.stage)
        assertNotNull(connection.disconnectReason)
    }

    @Test
    fun `a connection without a deadline keeps the stage it is in`() {
        context.switchStage(ConnectionStage.PRE_LOGIN)

        assertEquals(ConnectionStage.PRE_LOGIN, connection.stage)
        assertNull(connection.disconnectReason)
    }

    // ---------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------

    /** A step that records that it ran and then continues the chain. */
    private class RecordingStep(
        override val name: String,
        private val onRun: () -> Unit = {},
    ) : StagePacketStep<ConnectPacket> {
        override fun run(context: StageContext, packet: ConnectPacket, next: () -> Unit) {
            onRun()
            next()
        }
    }

    /** A step that asks the machine to move to another stage. */
    private class RequestingStep(
        override val name: String,
        private val target: ConnectionStage,
    ) : StagePacketStep<ConnectPacket> {
        override fun run(context: StageContext, packet: ConnectPacket, next: () -> Unit) {
            context.requestStage(target)
            next()
        }
    }

    private fun environment(): StageEnvironment = StageEnvironment(
        config = NetworkConfig(
            host = "127.0.0.1",
            port = 0,
            threadCount = 1,
            zstdCompressionLevel = 3,
            readTimeoutSeconds = 30,
            playTimeoutSeconds = 120,
            reuseAddress = true,
            rateLimitBurst = 0,
            rateLimitPacketsPerSecond = 0,
            serverName = "test",
            motd = "test",
            maxPlayers = 10,
            auth = AuthConfig(AuthMode.INSECURE, null, null, null, null, null),
        ),
        logger = TestLogger(),
        authExecutor = directExecutor(),
    )

    /** Executor that runs the tasks in the calling thread. */
    private fun directExecutor(): java.util.concurrent.ExecutorService = object : AbstractExecutorService() {
        override fun shutdown() = Unit
        override fun shutdownNow(): MutableList<Runnable> = mutableListOf()
        override fun isShutdown(): Boolean = false
        override fun isTerminated(): Boolean = false
        override fun awaitTermination(timeout: Long, unit: TimeUnit): Boolean = false
        override fun execute(command: Runnable) = command.run()
    }
}
