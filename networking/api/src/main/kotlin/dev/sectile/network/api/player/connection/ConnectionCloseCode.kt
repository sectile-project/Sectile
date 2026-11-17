package dev.sectile.network.api.player.connection

/**
 * Transport-agnostic application error codes used when closing a [PlayerConnection].
 */
enum class ConnectionCloseCode {
    NO_ERROR,
    PROTOCOL_VIOLATION,
    TIMEOUT,
    AUTH_FAILED,
    CLIENT_OUTDATED,
    SERVER_OUTDATED,
    RATE_LIMITED,
    CRASH,
}
