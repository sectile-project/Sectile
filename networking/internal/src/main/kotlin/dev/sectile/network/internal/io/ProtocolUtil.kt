package dev.sectile.network.internal.io

import dev.sectile.network.api.NetworkChannel
import dev.sectile.network.internal.quic.TokenBucket
import io.netty.util.AttributeKey
import java.time.Duration
import java.util.function.IntPredicate

/**
 * Role of a QUIC stream within a connection.
 */
enum class StreamRole(val channel: NetworkChannel) {
    /** Primary game stream (stream 0). */
    DEFAULT(NetworkChannel.DEFAULT),

    /** Client-opened auxiliary stream (voice). */
    VOICE_AUX(NetworkChannel.VOICE),

    /** Server-opened unidirectional chunks stream. */
    CHUNKS(NetworkChannel.CHUNKS),

    /** Server-opened unidirectional world map stream. */
    WORLD_MAP(NetworkChannel.WORLD_MAP),
}

object ProtocolUtil {

    val STREAM_ROLE_KEY: AttributeKey<StreamRole> = AttributeKey.valueOf("STREAM_ROLE")

    /**
     * Enforced network channel on server-created unidirectional streams (`CHUNKS` and `WORLD_MAP`).
     * The decoder rejects inbound packets whose definition belongs to a different channel.
     */
    val STREAM_CHANNEL_KEY: AttributeKey<NetworkChannel> = AttributeKey.valueOf("STREAM_CHANNEL_ID")

    /**
     * Optional per-stream packet id filter (used by pending auxiliary streams before stage routing).
     */
    val HANDLES_PACKET_KEY: AttributeKey<IntPredicate> = AttributeKey.valueOf("HANDLES_PACKET")

    /** Maximum idle time between complete packets read by [PacketDecoder]; `null` disables timeouts. */
    val PACKET_TIMEOUT_KEY: AttributeKey<Duration> = AttributeKey.valueOf("PACKET_TIMEOUT")

    /** Shared per-connection rate-limit bucket attached to the parent `QuicChannel`. */
    val RATE_LIMIT_KEY: AttributeKey<TokenBucket> = AttributeKey.valueOf("RATE_LIMIT_BUCKET")

    /** SNI hostname presented by the client during the TLS handshake. */
    val SNI_HOSTNAME_KEY: AttributeKey<String> = AttributeKey.valueOf("SNI_HOSTNAME")
}
