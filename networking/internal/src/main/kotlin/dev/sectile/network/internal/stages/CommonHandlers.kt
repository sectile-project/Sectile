package dev.sectile.network.internal.stages

import dev.sectile.network.api.packet.handler.PacketHandlerStorage
import dev.sectile.network.api.packet.handler.handle
import dev.sectile.network.api.packet.handler.on
import dev.sectile.network.api.player.connection.ConnectionCloseCode
import dev.sectile.network.protocol.ProtocolPacketTable
import dev.sectile.network.protocol.connection.ClientDisconnectPacket

/**
 * Registers the handler of the `ClientDisconnect` packet.
 *
 * The client may leave at any moment, so every stage registers this handler.
 *
 * @param environment server wide environment.
 * @param moment description of the moment the client left in, used in the log message.
 */
internal fun PacketHandlerStorage.onClientDisconnect(
    environment: StageEnvironment,
    moment: String = "",
) {
    on(ProtocolPacketTable.CLIENT_DISCONNECT, ::ClientDisconnectPacket) { connection, packet ->
        environment.logger.info(
            "[${connection.getIp()}] Client disconnected$moment: reason=${packet.reason}, type=${packet.type}",
        )
        connection.close(ConnectionCloseCode.NO_ERROR)
    }
}

/**
 * Registers the handler of the `ViewRadius` packet.
 *
 * The client asks for a different view radius at any moment: the value is kept in the session
 * state so the world can be streamed accordingly.
 */
internal fun PacketHandlerStorage.onViewRadius() {
    handle(ProtocolPacketTable.VIEW_RADIUS) { connection, buffer ->
        val value = buffer.readInt()
        if (value >= 0) {
            connection.updateSession { viewRadiusChunks = value }
        }
    }
}
