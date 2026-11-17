package dev.sectile.network.internal.packet

import dev.sectile.network.internal.io.DecodedPacket
import dev.sectile.network.internal.packet.buffer.NettyReadBuffer
import dev.sectile.network.internal.player.NettyPlayerConnection
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler

/**
 * Routes decoded inbound payloads to the handler storage of the connection's active stage.
 */
class PacketInboundHandler(
    private val connection: NettyPlayerConnection,
) : SimpleChannelInboundHandler<DecodedPacket>() {

    override fun channelRead0(ctx: ChannelHandlerContext, packet: DecodedPacket) {
        connection.currentStageStorage.callHandlers(
            connection,
            packet.definition.id,
            NettyReadBuffer(packet.payload),
        )
    }
}
