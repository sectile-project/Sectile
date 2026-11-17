package dev.sectile.network.internal.quic

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import io.netty.handler.codec.quic.QuicChannel
import io.netty.handler.codec.quic.QuicTransportError

/** QUIC connection closes with codes, as the original server does (connClose / PROTOCOL_VIOLATION). */
internal object QuicClose {

    fun quicChannelOf(channel: Channel): QuicChannel? = when {
        channel is QuicChannel -> channel
        channel.parent() is QuicChannel -> channel.parent() as QuicChannel
        else -> null
    }

    /** Silent close for a protocol violation (closes the whole connection, not just the stream). */
    fun protocolViolation(channel: Channel) {
        val quic = quicChannelOf(channel)
        if (quic != null) {
            quic.close(false, QuicTransportError.PROTOCOL_VIOLATION.code().toInt(), Unpooled.EMPTY_BUFFER)
        } else {
            channel.close()
        }
    }

    /** Application CONNECTION_CLOSE with a code and optional reason. */
    fun application(channel: Channel, code: QuicApplicationErrorCode, reason: ByteArray? = null) {
        val quic = quicChannelOf(channel)
        if (quic == null) {
            channel.close()
            return
        }
        val buf: ByteBuf =
            if (reason != null && reason.isNotEmpty() && reason.size <= QuicProtocol.MAX_CLOSE_REASON_BYTES) {
                Unpooled.wrappedBuffer(reason)
            } else {
                Unpooled.EMPTY_BUFFER
            }
        quic.close(true, code.value, buf)
    }
}
