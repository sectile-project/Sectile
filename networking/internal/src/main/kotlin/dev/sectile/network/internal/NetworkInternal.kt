package dev.sectile.network.internal

import dev.sectile.kernel.config.ConfigService
import dev.sectile.kernel.config.ConfigServiceRegistry
import dev.sectile.kernel.logger.Logger
import dev.sectile.network.api.Network
import dev.sectile.network.api.packet.PacketRegistry
import dev.sectile.network.api.packet.handler.PacketHandlerRegistry
import dev.sectile.network.api.packet.handler.PacketHandlerStorage
import dev.sectile.network.api.player.connection.ConnectionStage
import dev.sectile.network.internal.config.NetworkConfig
import dev.sectile.network.internal.packet.PacketSenderInternal
import dev.sectile.network.internal.packet.buffer.NettyWriteBuffer
import dev.sectile.network.internal.packet.storage.StatePacketHandlerStorage
import dev.sectile.network.internal.player.DisconnectSupport
import dev.sectile.network.internal.quic.QuicProtocol
import dev.sectile.network.internal.stages.StageEnvironment
import dev.sectile.network.internal.stages.StageRegistry
import dev.sectile.network.protocol.connection.enums.DisconnectType
import dev.sectile.network.protocol.connection.outbound.ServerDisconnectPacket
import dev.sectile.network.protocol.shared.text.FormattedMessage
import io.netty.buffer.Unpooled
import java.util.EnumMap
import java.util.concurrent.CompletableFuture

class NetworkInternal private constructor(
    private val config: NetworkConfig,
    private val connection: ServerConnection,
    private val jsonService: ConfigService // prevent random users override internal json
) {
    companion object {

        @Volatile
        private var ref: NetworkInternal? = null

        internal val stageStorages: Map<ConnectionStage, PacketHandlerStorage> =
            EnumMap<ConnectionStage, PacketHandlerStorage>(ConnectionStage::class.java).apply {
                for (stage in ConnectionStage.entries) {
                    put(stage, StatePacketHandlerStorage(PacketRegistry.MAX_PACKETS))
                }
            }

        /**
         * Active gameplay ([ConnectionStage.POST_LOGIN]) packet handler registry.
         *
         * Internal runtime components pass this explicitly; plugins access a scoped view via `PluginScope`.
         */
        val packetHandlers: PacketHandlerRegistry
            get() = stageStorages.getValue(ConnectionStage.POST_LOGIN)

        val config: NetworkConfig get() = connector.config

        val json: ConfigService get() {
            val instance = ref ?: return ConfigServiceRegistry["json"];
            return instance.jsonService
        }

        fun init(config: NetworkConfig, logger: Logger, jsonService: ConfigService): CompletableFuture<Unit> {
            if (ref != null) error("Network internal connector already initialized")

            val connection = ServerConnection()
            ref = NetworkInternal(config, connection, jsonService)

            Network.init(PacketSenderInternal())

            // Offload protocol/stage wiring so startup is not blocked by classloading Netty and stage handlers;
            // wiring completes before quic.bind accepts any client packet.
            val stagesFuture = CompletableFuture.runAsync {
                wireDisconnectSupport()
                StageRegistry.register(stageStorages, StageEnvironment.create(config, logger))
            }

            return stagesFuture.thenCompose { connection.init(config, logger) }
        }

        fun shutdown(): CompletableFuture<Unit> = connector.connection.shutdown()

        /** Wires protocol-level disconnect packet creation and QUIC close reason encoding. */
        private fun wireDisconnectSupport() {
            DisconnectSupport.packetFactory = { reason ->
                ServerDisconnectPacket(
                    reason = FormattedMessage().also { it.rawText = reason },
                    type = DisconnectType.DISCONNECT,
                )
            }
            DisconnectSupport.reasonEncoder = { reason ->
                val message = FormattedMessage().also { it.rawText = reason }
                val buf = Unpooled.buffer()
                try {
                    message.write(NettyWriteBuffer(buf))
                    val bytes = ByteArray(buf.readableBytes())
                    buf.readBytes(bytes)
                    if (bytes.size > QuicProtocol.MAX_CLOSE_REASON_BYTES) null else bytes
                } catch (_: Exception) {
                    null
                } finally {
                    buf.release()
                }
            }
        }

        private val connector: NetworkInternal
            get() = ref ?: error("Network internal connector not initialized")
    }
}
