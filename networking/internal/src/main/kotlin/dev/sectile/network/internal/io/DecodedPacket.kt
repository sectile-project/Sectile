package dev.sectile.network.internal.io

import dev.sectile.network.api.packet.PacketDefinition
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.util.AbstractReferenceCounted
import io.netty.util.ReferenceCounted

/**
 * Decoded and optionally decompressed inbound frame waiting to be dispatched to stage handlers.
 *
 * Implements [ReferenceCounted] so Netty automatically releases [payload] if a pipeline stage
 * short-circuits or throws before handlers run.
 */
class DecodedPacket(
    val definition: PacketDefinition,
    val payload: ByteBuf,
) : AbstractReferenceCounted() {

    override fun deallocate() {
        if (payload !== Unpooled.EMPTY_BUFFER && payload.refCnt() > 0) {
            payload.release()
        }
    }

    override fun touch(hint: Any?): ReferenceCounted = apply {
        payload.touch(hint)
    }
}
