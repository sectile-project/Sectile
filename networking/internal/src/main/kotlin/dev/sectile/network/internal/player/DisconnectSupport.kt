package dev.sectile.network.internal.player

import dev.sectile.network.api.packet.OutboundPacket

/**
 * Disconnect hooks decoupled from concrete protocol packet constructors:
 *
 *  - [packetFactory]: builds the outbound disconnect packet (e.g. `ServerDisconnectPacket`) from a reason key.
 *  - [reasonEncoder]: encodes the reason into raw `FormattedMessage` bytes (max 1000 bytes) for
 *    QUIC `CONNECTION_CLOSE` reason phrases; returns `null` if encoding fails or exceeds the limit.
 */
object DisconnectSupport {
    @Volatile
    var packetFactory: ((String) -> OutboundPacket)? = null

    @Volatile
    var reasonEncoder: ((String) -> ByteArray?)? = null
}
