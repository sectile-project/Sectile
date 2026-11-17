package dev.sectile.network.api.packet

import dev.sectile.network.api.player.connection.PlayerConnection

/**
 * High-performance dispatcher optimized for broadcasting outbound packets to multiple connections.
 *
 * Serializes each packet once and reuses the encoded frame across every target [PlayerConnection],
 * avoiding redundant encoding work compared to repeated [PlayerConnection.sendPacket] calls.
 */
interface PacketsSender {

    /**
     * Sends multiple packets efficiently to all [connections].
     */
    fun sendPackets(connections: Collection<PlayerConnection>, vararg packets: OutboundPacket)

    /**
     * Sends a single packet efficiently to all [connections].
     */
    fun sendPacket(connections: Collection<PlayerConnection>, packet: OutboundPacket)
}
