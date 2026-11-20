package dev.sectile.network.protocol.enums

/**
 * Reason for a disconnection initiated by the client.
 *
 * Equivalent to `ClientDisconnectReason` of the Hytale protocol.
 */
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
