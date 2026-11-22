package dev.sectile.network.protocol.player.enums

enum class GameMode {
    ADVENTURE,
    CREATIVE,
    ;

    companion object {
        fun of(ordinal: Int): GameMode? = entries.getOrNull(ordinal)
    }
}
