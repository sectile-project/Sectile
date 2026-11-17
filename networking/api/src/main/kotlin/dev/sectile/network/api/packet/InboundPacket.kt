package dev.sectile.network.api.packet

import dev.sectile.network.api.buffer.ReadBuffer

/**
 * Represents a packet that can be deserialized from bytes received from a client.
 */
interface InboundPacket {

    /** Protocol metadata entry from the packet registry/table. */
    val definition: PacketDefinition

    /** Reads this packet's payload from [buf]. */
    fun read(buf: ReadBuffer)
}
