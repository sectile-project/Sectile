package dev.sectile.network.api.player.connection

import dev.sectile.network.api.packet.OutboundPacket
import dev.sectile.network.api.player.MutablePlayerSessionState
import dev.sectile.network.api.player.PlayerAuthentication
import dev.sectile.network.api.player.PlayerSessionState
import dev.sectile.network.api.player.ScheduledTask
import java.security.cert.X509Certificate
import java.time.Duration
import java.util.concurrent.CompletableFuture

/**
 * Transport-independent representation of a player's network connection.
 *
 * All packet handlers and game modules interact exclusively through this interface without
 * depending on Netty internals.
 */
interface PlayerConnection {

    /** Current lifecycle stage of this connection. */
    val stage: ConnectionStage

    /** Current session state (position, entity id, readiness, identity). */
    val session: PlayerSessionState

    /** Authentication result once login completes, or `null` beforehand. */
    val authentication: PlayerAuthentication?

    /** Client mTLS certificate presented during the QUIC handshake, or `null`. */
    val clientCertificate: X509Certificate?

    /** SNI hostname requested by the client during the QUIC handshake, or `null`. */
    val sniHostname: String?

    /**
     * Sends an [OutboundPacket] to this player connection, automatically routing to
     * the stream matching [OutboundPacket.definition] channel.
     */
    fun sendPacket(packet: OutboundPacket)

    /**
     * Sends multiple [OutboundPacket]s efficiently, batching flushes per target stream.
     */
    fun sendPackets(vararg packets: OutboundPacket)

    /**
     * Transitions this connection to [stage], optionally updating the read timeout.
     */
    fun setStage(stage: ConnectionStage, readTimeout: Duration? = null)

    /** Updates the packet read timeout for the active stream (`null` disables timeout). */
    fun setReadTimeout(timeout: Duration?)

    /** Initializes session identity and entity id upon login completion. */
    fun beginSession(authentication: PlayerAuthentication, entityId: Int)

    /** Applies an atomic update to this connection's mutable session state. */
    fun updateSession(block: MutablePlayerSessionState.() -> Unit)

    /** Opens server-initiated unidirectional streams (chunks and world map). */
    fun openAuxiliaryStreams(): CompletableFuture<Void>

    /** Executes [task] on this connection's serialized event executor. */
    fun execute(task: Runnable)

    /** Schedules a one-shot [task] bound to this connection's lifetime. */
    fun schedule(delay: Duration, task: Runnable): ScheduledTask

    /** Schedules a repeating [task] bound to this connection's lifetime. */
    fun scheduleAtFixedRate(initialDelay: Duration, period: Duration, task: Runnable): ScheduledTask

    /** Registers a callback invoked once when the connection closes. */
    fun onDisconnect(listener: Runnable)

    /** Retrieves per-connection state associated with [key], or `null` if absent. */
    fun <T : Any> getAttribute(key: ConnectionKey<T>): T?

    /** Associates [value] with [key] on this connection (`null` removes the attribute). */
    fun <T : Any> setAttribute(key: ConnectionKey<T>, value: T?)

    /** Returns the value for [key], computing and storing it via [defaultValue] if absent. */
    fun <T : Any> getOrPutAttribute(key: ConnectionKey<T>, defaultValue: () -> T): T

    /**
     * Sends a graceful disconnect packet with [reason] and closes the connection.
     */
    fun disconnect(reason: String)

    /**
     * Immediately closes the transport connection with [code] and optional raw [reason] bytes,
     * without sending a disconnect packet.
     */
    fun close(code: ConnectionCloseCode = ConnectionCloseCode.NO_ERROR, reason: ByteArray? = null)

    /** Returns the client's remote address string. */
    fun getIp(): String

    /** Returns `true` if the underlying transport connection is active. */
    fun isOnline(): Boolean
}
