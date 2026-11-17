package dev.sectile.network.internal.packet

import dev.sectile.kernel.logger.logger
import dev.sectile.network.api.packet.OutboundPacket
import dev.sectile.network.api.packet.PacketsSender
import dev.sectile.network.api.player.connection.PlayerConnection
import dev.sectile.network.internal.io.PacketEncoder
import dev.sectile.network.internal.io.frame.PacketFramer
import dev.sectile.network.internal.player.NettyPlayerConnection

class PacketSenderInternal : PacketsSender {

    override fun sendPacket(
        connections: Collection<PlayerConnection>,
        packet: OutboundPacket,
    ) {
        if (connections.isEmpty()) {
            return
        }

        val buf = PacketEncoder.allocateBuf(packet) ?: return
        val cachedPacket = CachedPacket(buf, connections.size, packet.definition.channel)

        try {
            PacketFramer.writeFramedPacket(packet, buf)
        } catch (e: Exception) {
            repeat(connections.size) { cachedPacket.onCompleteWrite() }
            logger().error("Failed to write packet to buffer", e)
            return
        }

        var sentCount = 0
        for (connection in connections) {
            if (connection is NettyPlayerConnection) {
                connection.sendCachedPacket(cachedPacket)
                sentCount++
            }
        }

        // Release remaining reference counts for non-Netty connection implementations.
        val unsentCount = connections.size - sentCount
        repeat(unsentCount) { cachedPacket.onCompleteWrite() }
    }

    override fun sendPackets(
        connections: Collection<PlayerConnection>,
        vararg packets: OutboundPacket,
    ) {
        if (connections.isEmpty() || packets.isEmpty()) {
            return
        }

        for (packet in packets) {
            sendPacket(connections, packet)
        }
    }
}
