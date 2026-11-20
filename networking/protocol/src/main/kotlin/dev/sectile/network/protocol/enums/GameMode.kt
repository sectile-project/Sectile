package dev.sectile.network.protocol.enums

/**
 * Game mode. Equivalent to `GameMode` of the Hytale protocol.
 */
enum class GameMode {
    ADVENTURE,
    CREATIVE,
    ;

    companion object {
        fun of(ordinal: Int): GameMode? = entries.getOrNull(ordinal)
    }
}
