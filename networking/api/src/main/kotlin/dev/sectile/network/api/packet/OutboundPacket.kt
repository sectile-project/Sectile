package dev.sectile.network.api.packet

import dev.sectile.network.api.buffer.WriteBuffer

/**
 * Represents a packet that can be serialized and sent to a client.
 *
 * Metadata such as packet id, stream channel, maximum size, and Zstd compression
 * is provided by [definition], avoiding duplication across packet implementations.
 */
interface OutboundPacket {

    /** Protocol metadata entry from the packet registry/table. */
    val definition: PacketDefinition

    /** Writes this packet's payload into [buf]. */
    fun write(buf: WriteBuffer)

    /**
     * Estimated serialized payload size in bytes, used as a buffer allocation hint.
     *
     * Returns `0` when no size hint is provided.
     */
    fun size(): Int = 0
}
