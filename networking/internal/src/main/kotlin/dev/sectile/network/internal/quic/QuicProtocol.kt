package dev.sectile.network.internal.quic

import dev.sectile.network.protocol.ProtocolSettings
import java.time.Duration

/**
 * Transport constants aligned with the original Hytale server.
 */
object QuicProtocol {

    /** Protocol version. Defines the ALPN "hytale/N". */
    const val PROTOCOL_VERSION: Int = ProtocolSettings.PROTOCOL_VERSION

    /** Maximum allowed size for a packet's payload. */
    const val MAX_PACKET_SIZE: Int = ProtocolSettings.MAX_PACKET_SIZE

    const val ALPN_PREFIX = "hytale/"

    // Flow-control windows advertised to the client (initConfig of the original server).
    const val INITIAL_CONNECTION_WINDOW = 512L * 1024
    const val INITIAL_STREAM_WINDOW = 128L * 1024

    // The server does not accept unidirectional streams from the client.
    const val INITIAL_UNI_STREAM_WINDOW = 0L

    // bidi = 8 leaves room for reconnections (ids are cumulative); uni = 0 because the client opens no uni streams.
    const val MAX_BIDI_STREAMS = 8L
    const val MAX_UNI_STREAMS = 0L

    // 32 MiB: the receive buffer must absorb incoming bursts (see QUICTransport of the original server).
    const val SOCKET_RECEIVE_BUFFER_SIZE = 32 * 1024 * 1024

    /** Maximum number of bytes for the CONNECTION_CLOSE reason phrase. */
    const val MAX_CLOSE_REASON_BYTES = 1000

    /** Time limit for an auxiliary stream to identify itself (auxStreamPendingTimeout). */
    val AUX_STREAM_PENDING_TIMEOUT: Duration = Duration.ofSeconds(5)

    /** Advertised ALPN: current version and the previous one (the previous is later rejected with ClientOutdated). */
    fun alpnProtocols(): Array<String> = arrayOf(
        ALPN_PREFIX + PROTOCOL_VERSION,
        ALPN_PREFIX + (PROTOCOL_VERSION - 1),
    )

    /** Extracts N from "hytale/N", or null if it cannot be interpreted. */
    fun parseAlpnVersion(alpn: String?): Int? {
        if (alpn == null || !alpn.startsWith(ALPN_PREFIX)) return null
        return alpn.substring(ALPN_PREFIX.length).toIntOrNull()
    }
}
