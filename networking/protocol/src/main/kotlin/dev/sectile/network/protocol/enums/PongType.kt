package dev.sectile.network.protocol.enums

/**
 * Type of ping response. Equivalent to `PongType` of the Hytale protocol.
 */
enum class PongType {
    RAW,
    DIRECT,
    TICK,
    ;

    companion object {
        fun of(ordinal: Int): PongType? = entries.getOrNull(ordinal)
    }
}
