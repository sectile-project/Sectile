package dev.sectile.network.internal.packet

import dev.sectile.kernel.logger.logger
import dev.sectile.network.api.packet.OutboundPacket
import dev.sectile.network.api.packet.PacketsSender
import dev.sectile.network.api.player.connection.ConnectionHolder
import dev.sectile.network.api.player.connection.PlayerConnection
import dev.sectile.network.internal.io.PacketEncoder
import dev.sectile.network.internal.io.frame.PacketFramer
import dev.sectile.network.internal.player.NettyPlayerConnection

class PacketSenderInternal : PacketsSender {

    private fun createCachedPacket(connections: Int, packet: OutboundPacket): CachedPacket? {
        val buf = PacketEncoder.allocateBuf(packet) ?: return null
        val cachedPacket = CachedPacket(buf, connections, packet.definition.channel)

        try {
            PacketFramer.writeFramedPacket(packet, buf)
        } catch (e: Exception) {
            repeat(connections) { cachedPacket.onCompleteWrite() }
            logger().error("Failed to write packet to buffer", e)
            return null
        }
        return cachedPacket
    }

    private fun countHolders(items: Collection<Any>): Int {
        var count = 0
        for (item in items) if (item is ConnectionHolder) count++
        return count
    }

    private inline fun <T> dispatch(
        items: Collection<T>,
        count: Int,
        packet: OutboundPacket,
        resolve: (T) -> PlayerConnection?,
    ) {
        val cachedPacket = createCachedPacket(count, packet) ?: return

        var sentCount = 0
        for (item in items) {
            val connection = resolve(item)
            if (connection is NettyPlayerConnection && connection.isOnline()) {
                connection.sendCachedPacket(cachedPacket)
                sentCount++
            }
        }

        repeat(count - sentCount) { cachedPacket.onCompleteWrite() }
    }

    override fun sendPacket(connections: Collection<PlayerConnection>, packet: OutboundPacket) {
        if (connections.isEmpty()) return
        dispatch(connections, connections.size, packet) { it }
    }

    override fun sendPackets(connections: Collection<PlayerConnection>, vararg packets: OutboundPacket) {
        if (connections.isEmpty() || packets.isEmpty()) return
        for (packet in packets) {
            dispatch(connections, connections.size, packet) { it }
        }
    }

    override fun lazySendPacket(connections: Collection<Any>, packet: OutboundPacket) {
        val holders = countHolders(connections)
        if (holders == 0) return
        dispatch(connections, holders, packet) { (it as? ConnectionHolder)?.connection }
    }

    override fun lazySendPackets(connections: Collection<Any>, vararg packets: OutboundPacket) {
        val holders = countHolders(connections)
        if (holders == 0) return
        for (packet in packets) {
            dispatch(connections, holders, packet) { (it as? ConnectionHolder)?.connection }
        }
    }
}
