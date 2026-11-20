package dev.sectile.network.protocol

/**
 * Constants of the Hytale protocol.
 *
 * The values are those of the client build in use (258), which are the ones shipped in the
 * `HytaleServer.jar` installed by the client itself:
 * `PROTOCOL_CRC = -386561135` (uint32 `3908406161`), `PROTOCOL_VERSION = 3`,
 * `PROTOCOL_BUILD_NUMBER = 258`, `PACKET_COUNT = 389`.
 *
 * A client from another build is rejected with `ClientOutdated`, just as the original
 * server does.
 */
object ProtocolSettings {

    /**
     * Protocol CRC (uint32 `3908406161`).
     *
     * Stored as an unsigned `Long` so comparisons have no sign issues.
     */
    const val PROTOCOL_CRC: Long = 3908406161L

    /** Protocol version. Defines the ALPN `hytale/<version>`. */
    const val PROTOCOL_VERSION: Int = 3

    /** Protocol build number. */
    const val PROTOCOL_BUILD_NUMBER: Int = 258

    /** Number of distinct packets defined by the protocol. */
    const val PACKET_COUNT: Int = 389

    /** Maximum allowed size for a packet payload. */
    const val MAX_PACKET_SIZE: Int = 1_677_721_600

    /** `true` if the received CRC matches the protocol's. */
    fun validateCrc(crc: Int): Boolean = (crc.toLong() and 0xFFFFFFFFL) == PROTOCOL_CRC
}
