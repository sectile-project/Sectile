package dev.sectile.network.api.packet

/**
 * Bidirectional packet supporting both serialization and deserialization.
 */
interface Packet :
    OutboundPacket,
    InboundPacket
