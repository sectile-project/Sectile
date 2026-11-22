package dev.sectile.network.protocol.connection.enums

enum class ClientType {
    GAME,
    EDITOR,
    ;

    companion object {
        fun of(ordinal: Int): ClientType? = entries.getOrNull(ordinal)
    }
}
