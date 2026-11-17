package dev.sectile.network.internal

import io.netty.channel.IoHandlerFactory
import io.netty.channel.epoll.Epoll
import io.netty.channel.epoll.EpollDatagramChannel
import io.netty.channel.epoll.EpollIoHandler
import io.netty.channel.nio.NioIoHandler
import io.netty.channel.socket.DatagramChannel
import io.netty.channel.socket.nio.NioDatagramChannel

internal enum class NativeTransport(
    val ioHandlerFactory: () -> IoHandlerFactory,
    val datagramChannel: Class<out DatagramChannel>,
) {
    EPOLL({ EpollIoHandler.newFactory() }, EpollDatagramChannel::class.java),
    NIO({ NioIoHandler.newFactory() }, NioDatagramChannel::class.java), ;

    companion object {
        val current: NativeTransport by lazy {
            when {
                Epoll.isAvailable() -> EPOLL
                else -> NIO
            }
        }
    }
}
