package dev.sectile.network.internal.quic

/**
 * Token bucket with nanosecond refill (port of the original server's TokenBucket).
 * Not thread-safe: all streams of a QUIC connection run on the same event loop.
 */
class TokenBucket(
    private val maxTokens: Int,
    private val tokensPerSecond: Int,
    nowNs: Long,
) {
    private var tokens: Int = maxTokens
    private var lastRefillNs: Long = nowNs

    fun tryConsume(nowNs: Long): Boolean {
        val elapsedNs = nowNs - lastRefillNs
        if (elapsedNs > 0) {
            val toAdd = (elapsedNs * tokensPerSecond) / 1_000_000_000L
            if (toAdd > 0) {
                tokens = minOf(maxTokens.toLong(), tokens + toAdd).toInt()
                // Only advance the nanos that produced whole tokens; the rest is carried over.
                lastRefillNs += toAdd * 1_000_000_000L / tokensPerSecond
            }
        }
        if (tokens <= 0) return false
        tokens--
        return true
    }
}
