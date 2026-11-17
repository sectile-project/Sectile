package dev.sectile.network.internal.stages

import dev.sectile.network.api.packet.OutboundPacket
import dev.sectile.network.api.player.MutablePlayerSessionState
import dev.sectile.network.api.player.PlayerAuthentication
import dev.sectile.network.api.player.PlayerSessionState
import dev.sectile.network.api.player.ScheduledTask
import dev.sectile.network.api.player.connection.ConnectionCloseCode
import dev.sectile.network.api.player.connection.ConnectionKey
import dev.sectile.network.api.player.connection.ConnectionStage
import dev.sectile.network.api.player.connection.PlayerConnection
import java.security.cert.X509Certificate
import java.time.Duration
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

/**
 * Test double of a player connection.
 *
 * It records what the stage machine does with the connection, without needing a transport.
 */
class FakePlayerConnection : PlayerConnection {

    /** Mutable session state of the fake player. */
    class FakeSessionState : MutablePlayerSessionState {
        override var uuid: UUID = UUID(0L, 0L)
        override var username: String = ""
        override var entityId: Int = 0
        override var ready: Boolean = false
        override var viewRadiusChunks: Int = 6
        override var positionX: Double = 0.0
        override var positionY: Double = 0.0
        override var positionZ: Double = 0.0
        override var yaw: Float = 0f
        override var pitch: Float = 0f
    }

    override var stage: ConnectionStage = ConnectionStage.HANDSHAKE
    override val session: PlayerSessionState = FakeSessionState()
    override var authentication: PlayerAuthentication? = null
    override val clientCertificate: X509Certificate? = null
    override val sniHostname: String? = null

    /** Packets the stage machine sent to the client. */
    val sentPackets = mutableListOf<OutboundPacket>()

    /** Reason of the graceful disconnect, or `null` when the connection was not disconnected. */
    var disconnectReason: String? = null

    /** Code of the transport level close, or `null` when the connection was not closed. */
    var closeCode: ConnectionCloseCode? = null

    /** Stages the connection went through, in order. */
    val stageHistory = mutableListOf(stage)

    private val attributes = ConcurrentHashMap<ConnectionKey<*>, Any>()

    override fun sendPacket(packet: OutboundPacket) {
        sentPackets += packet
    }

    override fun sendPackets(vararg packets: OutboundPacket) {
        sentPackets += packets
    }

    override fun setStage(stage: ConnectionStage, readTimeout: Duration?) {
        this.stage = stage
        stageHistory += stage
    }

    override fun setReadTimeout(timeout: Duration?) = Unit

    override fun beginSession(authentication: PlayerAuthentication, entityId: Int) {
        this.authentication = authentication
        val state = session as FakeSessionState
        state.uuid = authentication.uuid
        state.username = authentication.username
        state.entityId = entityId
    }

    override fun updateSession(block: MutablePlayerSessionState.() -> Unit) {
        (session as MutablePlayerSessionState).block()
    }

    override fun openAuxiliaryStreams(): CompletableFuture<Void> = CompletableFuture.completedFuture(null)

    override fun execute(task: Runnable) = task.run()

    override fun schedule(delay: Duration, task: Runnable): ScheduledTask {
        task.run()
        return ScheduledTask { }
    }

    override fun scheduleAtFixedRate(initialDelay: Duration, period: Duration, task: Runnable): ScheduledTask = ScheduledTask { }

    override fun onDisconnect(listener: Runnable) = Unit

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> getAttribute(key: ConnectionKey<T>): T? = attributes[key] as? T

    override fun <T : Any> setAttribute(key: ConnectionKey<T>, value: T?) {
        if (value == null) attributes.remove(key) else attributes[key] = value
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> getOrPutAttribute(key: ConnectionKey<T>, defaultValue: () -> T): T = attributes.computeIfAbsent(key) { defaultValue() } as T

    override fun disconnect(reason: String) {
        disconnectReason = reason
    }

    override fun close(code: ConnectionCloseCode, reason: ByteArray?) {
        closeCode = code
    }

    override fun getIp(): String = "/127.0.0.1:12345"

    override fun isOnline(): Boolean = true
}
