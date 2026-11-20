package dev.sectile.network.protocol.enums

/**
 * Type of stream that is opened. Equivalent to `StreamType` of the Hytale protocol.
 */
enum class StreamType {
    GAME,
    VOICE,
    ;

    companion object {
        fun of(ordinal: Int): StreamType? = entries.getOrNull(ordinal)
    }
}
