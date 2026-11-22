package dev.sectile.network.protocol.connection.enums

enum class DisconnectType {
    DISCONNECT,
    CRASH,
    ;

    companion object {
        fun of(ordinal: Int): DisconnectType? = entries.getOrNull(ordinal)
    }
}
