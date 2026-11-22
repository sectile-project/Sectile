package dev.sectile.network.protocol.player.enums

enum class StreamType {
    GAME,
    VOICE,
    ;

    companion object {
        fun of(ordinal: Int): StreamType? = entries.getOrNull(ordinal)
    }
}
