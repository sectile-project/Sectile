package dev.sectile.network.internal.quic

/**
 * Application error codes for CONNECTION_CLOSE.
 *
 * The numeric values must match `QuicApplicationErrorCode` from the Hytale protocol:
 * the client interprets the reason for the close from them.
 */
enum class QuicApplicationErrorCode(val value: Int) {
    NoError(0),
    RateLimited(1),
    AuthFailed(2),
    InvalidVersion(3),
    Timeout(4),
    ClientOutdated(5),
    ServerOutdated(6),
    Crash(7),
}
