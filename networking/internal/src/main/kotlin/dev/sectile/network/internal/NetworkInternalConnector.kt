package dev.sectile.network.internal

import dev.sectile.kernel.logger.Logger
import dev.sectile.network.api.NetworkConnector
import dev.sectile.network.api.packet.PacketRegistry
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
import dev.sectile.network.protocol.connection.ServerDisconnectPacket
import dev.sectile.network.protocol.enums.DisconnectType
import dev.sectile.network.protocol.structures.FormattedMessage
import io.netty.buffer.Unpooled
import java.util.concurrent.CompletableFuture

class NetworkInternalConnector private constructor(
    private val config: NetworkConfig,
    private val connection: ServerConnection,
    private val stageStorages: Map<ConnectionStage, PacketHandlerStorage>,
) {
    companion object {

        @Volatile
        private var ref: NetworkInternalConnector? = null

        val config: NetworkConfig get() = connector.config
        internal val stageStorages: Map<ConnectionStage, PacketHandlerStorage> get() = connector.stageStorages

        fun init(config: NetworkConfig, logger: Logger): CompletableFuture<Unit> {
            if (ref != null) error("Network internal connector already initialized")

            // Register all protocol packet definitions before accepting client streams.
            wireDisconnectSupport()

            val storages = ConnectionStage.entries.associateWith {
                StatePacketHandlerStorage(PacketRegistry.MAX_PACKETS)
            }

            // The stage machine owns the whole lifecycle of a connection: every stage
            // registers the handlers of the packets it waits for.
            StageRegistry.register(storages, StageEnvironment.create(config, logger))

            val connection = ServerConnection()
            ref = NetworkInternalConnector(config, connection, storages)

            NetworkConnector.init(storages, PacketSenderInternal())

            return connection.init(config, logger)
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

        private val connector: NetworkInternalConnector
            get() = ref ?: error("Network internal connector not initialized")
    }
}
