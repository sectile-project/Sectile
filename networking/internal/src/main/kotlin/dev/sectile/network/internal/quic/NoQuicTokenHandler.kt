package dev.sectile.network.internal.quic

import io.netty.buffer.ByteBuf
import io.netty.handler.codec.quic.QuicTokenHandler
import java.net.InetSocketAddress

/**
 * [QuicTokenHandler] which will not use any token and so also fail to validate any given token.
 */
internal class NoQuicTokenHandler private constructor() : QuicTokenHandler {
    override fun writeToken(out: ByteBuf?, dcid: ByteBuf?, address: InetSocketAddress?): Boolean = false

    override fun validateToken(token: ByteBuf?, address: InetSocketAddress?): Int = -1

    override fun maxTokenLength(): Int = 0

    companion object {
        val INSTANCE: QuicTokenHandler = NoQuicTokenHandler()
    }
}
