package dev.sectile.network.api.packet

/**
 * Network direction in which a packet is allowed to travel.
 *
 * Mirrors Hytale's `PacketDirection` enum. Declaration order is significant.
 */
enum class PacketDirection {
    TO_SERVER,
    TO_CLIENT,
    BOTH,
    ;

    /** `true` if the client may send this packet to the server. */
    val inbound: Boolean
        get() = this != TO_CLIENT

    /** `true` if the server may send this packet to the client. */
    val outbound: Boolean
        get() = this != TO_SERVER

    companion object {
        /** Returns the [PacketDirection] matching [ordinal], or `null` if out of range. */
        fun of(ordinal: Int): PacketDirection? = entries.getOrNull(ordinal)
    }
}
