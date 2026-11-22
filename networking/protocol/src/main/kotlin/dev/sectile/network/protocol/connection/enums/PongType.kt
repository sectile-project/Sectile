package dev.sectile.network.protocol.connection.enums

enum class PongType {
    RAW,
    DIRECT,
    TICK,
    ;

    companion object {
        fun of(ordinal: Int): PongType? = entries.getOrNull(ordinal)
    }
}
