package dev.sectile.network.internal.player

import dev.sectile.kernel.Kernel
import dev.sectile.kernel.logger.Logger
import dev.sectile.network.api.NetworkChannel
import dev.sectile.network.api.packet.OutboundPacket
import dev.sectile.network.api.packet.handler.PacketHandlerStorage
import dev.sectile.network.api.player.MutablePlayerSessionState
import dev.sectile.network.api.player.PlayerAuthentication
import dev.sectile.network.api.player.PlayerSessionState
import dev.sectile.network.api.player.ScheduledTask
import dev.sectile.network.api.player.connection.ConnectionCloseCode
import dev.sectile.network.api.player.connection.ConnectionKey
import dev.sectile.network.api.player.connection.ConnectionStage
import dev.sectile.network.api.player.connection.PlayerConnection
import dev.sectile.network.internal.io.PacketEncoder
import dev.sectile.network.internal.io.ProtocolUtil
import dev.sectile.network.internal.io.StreamRole
import dev.sectile.network.internal.packet.CachedPacket
import dev.sectile.network.internal.quic.QuicApplicationErrorCode
import dev.sectile.network.internal.quic.QuicClose
import dev.sectile.network.internal.quic.QuicTransport
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.ChannelInitializer
import io.netty.handler.codec.quic.QuicChannel
import io.netty.handler.codec.quic.QuicStreamChannel
import io.netty.handler.codec.quic.QuicStreamPriority
import io.netty.handler.codec.quic.QuicStreamType
import io.netty.util.AttributeKey
import io.netty.util.ReferenceCountUtil
import java.security.cert.X509Certificate
import java.time.Duration
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class NettyPlayerConnection(
    val ctx: Channel,
    private val logger: Logger,
    private val stageStorages: Map<ConnectionStage, PacketHandlerStorage>,
) : PlayerConnection {

    companion object {
        /** Primary stream (id 0) connection attached to the parent `QuicChannel` for auxiliary streams. */
        val PRIMARY_CONNECTION_KEY: AttributeKey<NettyPlayerConnection> =
            AttributeKey.valueOf("PRIMARY_PLAYER_CONNECTION")

        private const val DEFAULT_VIEW_RADIUS_CHUNKS = 6
    }

    private class MutableSessionState : MutablePlayerSessionState {
        @Volatile override var uuid: UUID = UUID(0L, 0L)

        @Volatile override var username: String = ""

        @Volatile override var entityId: Int = 0

        @Volatile override var ready: Boolean = false

        @Volatile override var viewRadiusChunks: Int = DEFAULT_VIEW_RADIUS_CHUNKS
    }

    private class NettyScheduledTask(
        private val future: ScheduledFuture<*>,
        private val activeTasks: MutableSet<ScheduledTask>,
    ) : ScheduledTask {
        private val scopeHandle = AtomicReference<AutoCloseable?>()
        private val released = AtomicBoolean(false)

        fun attachScopeHandle(handle: AutoCloseable?) {
            if (handle == null) return
            if (released.get() || !scopeHandle.compareAndSet(null, handle)) {
                handle.close()
            }
        }

        fun releaseScopeHandle() {
            released.set(true)
            scopeHandle.getAndSet(null)?.close()
        }

        override fun cancel() {
            future.cancel(false)
            activeTasks.remove(this)
            releaseScopeHandle()
        }
    }

    private class DisconnectEntry(
        private val delegate: Runnable,
    ) : Runnable {
        private val scopeHandle = AtomicReference<AutoCloseable?>()
        private val released = AtomicBoolean(false)

        fun attachScopeHandle(handle: AutoCloseable?) {
            if (handle == null) return
            if (released.get() || !scopeHandle.compareAndSet(null, handle)) {
                handle.close()
            }
        }

        fun releaseScopeHandle() {
            released.set(true)
            scopeHandle.getAndSet(null)?.close()
        }

        override fun run() {
            releaseScopeHandle()
            delegate.run()
        }
    }

    private object CancelledScheduledTask : ScheduledTask {
        override fun cancel() = Unit
    }

    /** Parent QUIC connection, or `null` if the channel is not a QUIC stream. */
    private val quic: QuicChannel? = QuicClose.quicChannelOf(ctx)

    private val closing = AtomicBoolean(false)
    private val disconnectNotified = AtomicBoolean(false)
    private val sessionLock = Any()
    private val attributes = ConcurrentHashMap<ConnectionKey<*>, Any>()
    private val disconnectListeners = ConcurrentLinkedQueue<DisconnectEntry>()
    private val scheduledTasks = ConcurrentHashMap.newKeySet<ScheduledTask>()

    @Volatile
    override var stage: ConnectionStage = ConnectionStage.HANDSHAKE
        private set

    @Volatile
    internal var currentStageStorage: PacketHandlerStorage = stageStorages.getValue(ConnectionStage.HANDSHAKE)
        private set

    override val session: PlayerSessionState
        field = MutableSessionState()

    @Volatile
    override var authentication: PlayerAuthentication? = null
        private set

    override val clientCertificate: X509Certificate?
        get() = ctx.attr(QuicTransport.CLIENT_CERTIFICATE_ATTR).get()

    override val sniHostname: String?
        get() = quic?.attr(ProtocolUtil.SNI_HOSTNAME_KEY)?.get()

    @Volatile
    var chunksChannel: Channel? = null
        private set

    @Volatile
    var worldMapChannel: Channel? = null
        private set

    init {
        ctx.closeFuture().addListener { notifyDisconnect() }
    }

    override fun sendPacket(packet: OutboundPacket) {
        writeTo(channelFor(packet.definition.channel), packet)
    }

    override fun sendPackets(vararg packets: OutboundPacket) {
        if (packets.isEmpty() || !isOnline()) return

        val task = Runnable {
            val chunks = chunksChannel
            val worldMap = worldMapChannel
            var flushDefault = false
            var flushChunks = false
            var flushWorldMap = false

            for (packet in packets) {
                val target = channelFor(packet.definition.channel)
                if (!target.isActive) continue
                when (target) {
                    chunks -> {
                        chunks.write(packet)
                        flushChunks = true
                    }

                    worldMap -> {
                        worldMap.write(packet)
                        flushWorldMap = true
                    }

                    else -> {
                        ctx.write(packet)
                        flushDefault = true
                    }
                }
            }

            if (flushDefault) ctx.flush()
            if (flushChunks) chunks?.flush()
            if (flushWorldMap) worldMap?.flush()
        }

        if (ctx.eventLoop().inEventLoop()) {
            task.run()
        } else {
            ctx.eventLoop().execute(task)
        }
    }

    override fun setStage(stage: ConnectionStage, readTimeout: Duration?) {
        this.stage = stage
        this.currentStageStorage = stageStorages.getValue(stage)
        if (readTimeout != null) {
            setReadTimeout(readTimeout)
        }
    }

    override fun setReadTimeout(timeout: Duration?) {
        ctx.attr(ProtocolUtil.PACKET_TIMEOUT_KEY).set(timeout)
    }

    override fun beginSession(authentication: PlayerAuthentication, entityId: Int) {
        synchronized(sessionLock) {
            this.authentication = authentication
            session.uuid = authentication.uuid
            session.username = authentication.username
            session.entityId = entityId
        }
    }

    override fun updateSession(block: MutablePlayerSessionState.() -> Unit) {
        synchronized(sessionLock) {
            session.block()
        }
    }

    override fun openAuxiliaryStreams(): CompletableFuture<Void> = setupAuxiliaryChannels()

    override fun execute(task: Runnable) {
        if (ctx.eventLoop().inEventLoop()) {
            task.run()
        } else {
            ctx.eventLoop().execute(task)
        }
    }

    override fun schedule(delay: Duration, task: Runnable): ScheduledTask {
        if (disconnectNotified.get()) return CancelledScheduledTask

        var handle: NettyScheduledTask? = null
        val wrapped = Runnable {
            try {
                if (!disconnectNotified.get()) {
                    task.run()
                }
            } finally {
                handle?.let {
                    scheduledTasks.remove(it)
                    it.releaseScopeHandle()
                }
            }
        }
        val future = ctx.eventLoop().schedule(wrapped, maxOf(0L, delay.toNanos()), TimeUnit.NANOSECONDS)
        val scheduled = NettyScheduledTask(future, scheduledTasks)
        handle = scheduled
        scheduledTasks.add(scheduled)
        scheduled.attachScopeHandle(bindPluginScope(task) { scheduled.cancel() })
        if (disconnectNotified.get()) {
            scheduled.cancel()
        }
        return scheduled
    }

    override fun scheduleAtFixedRate(initialDelay: Duration, period: Duration, task: Runnable): ScheduledTask {
        if (disconnectNotified.get()) return CancelledScheduledTask

        val wrapped = Runnable {
            if (!disconnectNotified.get()) {
                task.run()
            }
        }
        val future = ctx.eventLoop().scheduleAtFixedRate(
            wrapped,
            maxOf(0L, initialDelay.toNanos()),
            maxOf(1L, period.toNanos()),
            TimeUnit.NANOSECONDS,
        )
        val scheduled = NettyScheduledTask(future, scheduledTasks)
        scheduledTasks.add(scheduled)
        scheduled.attachScopeHandle(bindPluginScope(task) { scheduled.cancel() })
        if (disconnectNotified.get()) {
            scheduled.cancel()
        }
        return scheduled
    }

    override fun onDisconnect(listener: Runnable): AutoCloseable {
        if (disconnectNotified.get()) {
            listener.run()
            return AutoCloseable { }
        }
        val entry = DisconnectEntry(listener)
        disconnectListeners.add(entry)
        if (disconnectNotified.get() && disconnectListeners.remove(entry)) {
            entry.run()
            return AutoCloseable { }
        }
        entry.attachScopeHandle(
            bindPluginScope(listener) {
                disconnectListeners.remove(entry)
                entry.releaseScopeHandle()
            },
        )
        if (disconnectNotified.get() && disconnectListeners.remove(entry)) {
            entry.releaseScopeHandle()
            entry.run()
            return AutoCloseable { }
        }
        return AutoCloseable {
            disconnectListeners.remove(entry)
            entry.releaseScopeHandle()
        }
    }

    private fun bindPluginScope(owner: Any, onScopeClose: () -> Unit): AutoCloseable? {
        if (!Kernel.isInitialized()) return null
        return runCatching { Kernel.pluginScopeFactory.acquire(owner).onClose(onScopeClose) }.getOrNull()
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> getAttribute(key: ConnectionKey<T>): T? = attributes[key] as? T

    override fun <T : Any> setAttribute(key: ConnectionKey<T>, value: T?) {
        if (value == null) {
            attributes.remove(key)
        } else {
            attributes[key] = value
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> getOrPutAttribute(key: ConnectionKey<T>, defaultValue: () -> T): T = attributes.computeIfAbsent(key) { defaultValue() } as T

    private fun channelFor(channel: NetworkChannel): Channel = when (channel) {
        NetworkChannel.CHUNKS -> chunksChannel ?: ctx
        NetworkChannel.WORLD_MAP -> worldMapChannel ?: ctx
        NetworkChannel.DEFAULT, NetworkChannel.VOICE -> ctx
    }

    private fun writeTo(target: Channel, packet: OutboundPacket) {
        if (!isOnline()) return

        if (target.eventLoop().inEventLoop()) {
            target.writeAndFlush(packet)
        } else {
            target.eventLoop().execute { target.writeAndFlush(packet) }
        }
    }

    fun sendCachedPacket(packet: CachedPacket) {
        val target = channelFor(packet.channel)
        if (!isOnline() || !target.isActive) {
            packet.onCompleteWrite()
            return
        }

        val buf = packet.retainForWrite()
        if (buf == null) {
            packet.onCompleteWrite()
            return
        }

        val writeTask = Runnable {
            target.writeAndFlush(buf).addListener { future ->
                try {
                    if (!future.isSuccess) {
                        logger.warn("Failed to send cached packet: ${future.cause()?.message}")
                    }
                } finally {
                    packet.onCompleteWrite()
                }
            }
        }

        if (target.eventLoop().inEventLoop()) {
            writeTask.run()
        } else {
            target.eventLoop().execute(writeTask)
        }
    }

    /**
     * Opens the server-initiated unidirectional streams: Chunks (urgency 0, incremental) and WorldMap
     * (urgency 1, incremental). Created in this order so QUIC stream ids match 3 and 7.
     */
    fun setupAuxiliaryChannels(): CompletableFuture<Void> {
        val result = CompletableFuture<Void>()
        val quicChannel = quic
        if (quicChannel == null || !quicChannel.isActive) {
            result.complete(null)
            return result
        }

        quicChannel.eventLoop().execute {
            createUniStream(quicChannel, StreamRole.CHUNKS, urgency = 0) { chunks ->
                if (chunks == null) {
                    result.completeExceptionally(IllegalStateException("Failed to create Chunks stream"))
                    return@createUniStream
                }
                chunksChannel = chunks
                createUniStream(quicChannel, StreamRole.WORLD_MAP, urgency = 1) { worldMap ->
                    if (worldMap == null) {
                        result.completeExceptionally(IllegalStateException("Failed to create WorldMap stream"))
                        return@createUniStream
                    }
                    worldMapChannel = worldMap
                    result.complete(null)
                }
            }
        }
        return result
    }

    private fun createUniStream(
        quicChannel: QuicChannel,
        role: StreamRole,
        urgency: Int,
        onDone: (QuicStreamChannel?) -> Unit,
    ) {
        quicChannel.createStream(
            QuicStreamType.UNIDIRECTIONAL,
            object : ChannelInitializer<QuicStreamChannel>() {
                override fun initChannel(ch: QuicStreamChannel) {
                    ch.attr(ProtocolUtil.STREAM_ROLE_KEY).set(role)
                    ch.attr(ProtocolUtil.STREAM_CHANNEL_KEY).set(role.channel)
                    ch.pipeline()
                        .addLast("encoder", PacketEncoder.INSTANCE)
                        // Clients must never send inbound data over server-initiated uni streams.
                        .addLast(
                            "reject_inbound",
                            object : ChannelInboundHandlerAdapter() {
                                override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
                                    ReferenceCountUtil.release(msg)
                                    QuicClose.protocolViolation(ctx.channel())
                                }
                            },
                        )
                }
            },
        ).addListener { future ->
            if (future.isSuccess) {
                val stream = future.now as QuicStreamChannel
                stream.updatePriority(QuicStreamPriority(urgency, true))
                onDone(stream)
            } else {
                logger.warn("Failed to create $role stream: ${future.cause()?.message}")
                onDone(null)
            }
        }
    }

    override fun disconnect(reason: String) {
        if (!closing.compareAndSet(false, true)) return
        logger.info("Disconnect: ${getIp()}. Reason: $reason")

        val task = Runnable {
            val closeConnection = {
                QuicClose.application(
                    ctx,
                    QuicApplicationErrorCode.NoError,
                    DisconnectSupport.reasonEncoder?.invoke(reason),
                )
            }
            val disconnectPacket = DisconnectSupport.packetFactory?.invoke(reason)
            if (disconnectPacket != null && ctx.isActive) {
                ctx.writeAndFlush(disconnectPacket).addListener { closeConnection() }
            } else {
                closeConnection()
            }
        }

        execute(task)
    }

    override fun close(code: ConnectionCloseCode, reason: ByteArray?) {
        if (!closing.compareAndSet(false, true)) return

        execute {
            when (code) {
                ConnectionCloseCode.PROTOCOL_VIOLATION -> QuicClose.protocolViolation(ctx)
                ConnectionCloseCode.NO_ERROR -> QuicClose.application(ctx, QuicApplicationErrorCode.NoError, reason)
                ConnectionCloseCode.TIMEOUT -> QuicClose.application(ctx, QuicApplicationErrorCode.Timeout, reason)
                ConnectionCloseCode.AUTH_FAILED -> QuicClose.application(ctx, QuicApplicationErrorCode.AuthFailed, reason)
                ConnectionCloseCode.CLIENT_OUTDATED -> QuicClose.application(ctx, QuicApplicationErrorCode.ClientOutdated, reason)
                ConnectionCloseCode.SERVER_OUTDATED -> QuicClose.application(ctx, QuicApplicationErrorCode.ServerOutdated, reason)
                ConnectionCloseCode.RATE_LIMITED -> QuicClose.application(ctx, QuicApplicationErrorCode.RateLimited, reason)
                ConnectionCloseCode.CRASH -> QuicClose.application(ctx, QuicApplicationErrorCode.Crash, reason)
            }
        }
    }

    override fun isOnline(): Boolean = ctx.isActive && (quic?.isActive ?: true)

    override fun getIp(): String = (quic?.remoteSocketAddress() ?: ctx.remoteAddress()).toString()

    private fun notifyDisconnect() {
        if (!disconnectNotified.compareAndSet(false, true)) return

        val tasks = scheduledTasks.toList()
        scheduledTasks.clear()
        for (task in tasks) {
            task.cancel()
        }

        while (true) {
            val listener = disconnectListeners.poll() ?: break
            try {
                listener.run()
            } catch (e: Exception) {
                logger.warn("Error in disconnect listener: ${e.javaClass.simpleName}: ${e.message}")
            }
        }
    }
}
