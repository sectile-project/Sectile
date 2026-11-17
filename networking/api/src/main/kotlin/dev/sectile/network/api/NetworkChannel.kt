package dev.sectile.network.api

/**
 * Logical QUIC stream channel on which a packet travels.
 *
 * Matches Hytale's `NetworkChannel` protocol enum:
 * - [DEFAULT]: primary bidirectional stream (always present).
 * - [CHUNKS]: server-to-client unidirectional stream for chunk data.
 * - [WORLD_MAP]: server-to-client unidirectional stream for world map data (low priority).
 * - [VOICE]: client-initiated bidirectional stream opened via `StreamOpen`.
 *
 * Declaration order matters because the enum ordinal is serialized on the wire.
 */
enum class NetworkChannel {
    DEFAULT,
    CHUNKS,
    WORLD_MAP,
    VOICE,
    ;

    companion object {
        /** Returns the [NetworkChannel] matching [ordinal], or `null` if out of range. */
        fun of(ordinal: Int): NetworkChannel? = entries.getOrNull(ordinal)
    }
}
