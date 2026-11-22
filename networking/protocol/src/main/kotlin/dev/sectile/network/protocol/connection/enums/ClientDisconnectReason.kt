package dev.sectile.network.protocol.connection.enums

enum class ClientDisconnectReason {
    PLAYER_LEAVE,
    PLAYER_ABORT,
    USER_LEAVE,
    CRASH,
    ;

    companion object {
        fun of(ordinal: Int): ClientDisconnectReason? = entries.getOrNull(ordinal)
    }
}
