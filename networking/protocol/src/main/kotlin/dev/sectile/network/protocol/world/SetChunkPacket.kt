package dev.sectile.network.protocol.world

import dev.sectile.network.api.buffer.WriteBuffer
import dev.sectile.network.api.packet.OutboundPacket
import dev.sectile.network.api.packet.PacketDefinition
import dev.sectile.network.protocol.ProtocolPacketTable

/**
 * Outbound `SetChunk` packet (id 131, server -> client, Zstd compressed, chunks channel).
 *
 * Sends a chunk section `(x, y, z)`. When all three optional arrays (`localLight`, `globalLight`,
 * and `data`) are omitted (`nullBits = 0x00`), the client marks column `(x, z)` as empty
 * (`IsEmpty = true`), finishing world load immediately instead of waiting for the 30s timeout.
 */
class SetChunkPacket(
    var x: Int = 0,
    var y: Int = 0,
    var z: Int = 0,
) : OutboundPacket {

    override val definition: PacketDefinition
        get() = ProtocolPacketTable.SET_CHUNK

    override fun write(buf: WriteBuffer) {
        buf.writeByte(0x00) // no localLight, globalLight, or data
        buf.writeInt(x)
        buf.writeInt(y)
        buf.writeInt(z)
        buf.writeInt(-1) // localLight offset slot
        buf.writeInt(-1) // globalLight offset slot
        buf.writeInt(-1) // data offset slot
    }

    override fun size(): Int = EMPTY_SIZE

    companion object {
        /** Fixed block size when all three optional arrays are omitted. */
        const val EMPTY_SIZE: Int = 25
    }
}
