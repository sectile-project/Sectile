package dev.sectile.network.internal.packet

import dev.sectile.network.api.NetworkChannel
import io.netty.buffer.ByteBuf

/**
 * Pre-serialized framed packet buffer shared across broadcast recipients.
 *
 * Tracks remaining sends via [ttl] so the underlying Netty [ByteBuf] is released once every
 * target connection finishes writing it.
 */
class CachedPacket(
    private val serializedBuf: ByteBuf,
    private var ttl: Int,
    val channel: NetworkChannel = NetworkChannel.DEFAULT,
) {
    private val lock = Any()

    fun retainForWrite(): ByteBuf? = synchronized(lock) {
        if (ttl <= 0 || serializedBuf.refCnt() <= 0) return null
        return serializedBuf.duplicate().retain()
    }

    fun onCompleteWrite() = synchronized(lock) {
        if (ttl > 0) {
            ttl--
            if (ttl <= 0 && serializedBuf.refCnt() > 0) {
                serializedBuf.release()
            }
        }
    }
}
