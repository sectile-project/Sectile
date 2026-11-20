package dev.sectile.network.protocol

/**
 * Protocol format error: the received buffer does not match the expected layout.
 *
 * The network decoder catches this exception (and any other) and closes the connection
 * for a protocol violation.
 */
class ProtocolFormatException(message: String) : RuntimeException(message)
