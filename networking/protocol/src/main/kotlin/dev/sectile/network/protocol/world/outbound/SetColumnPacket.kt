package dev.sectile.network.protocol.world.outbound

import dev.sectile.network.api.buffer.WriteBuffer
import dev.sectile.network.api.packet.OutboundPacket
import dev.sectile.network.api.packet.PacketDefinition
import dev.sectile.network.protocol.DataSize
import dev.sectile.network.protocol.ProtocolPacketTable

/**
 * Must be sent before any `SetChunk` packet in the same column `(x, z)`.
 */
class SetColumnPacket(
    var x: Int = 0,
    var z: Int = 0,
) : OutboundPacket {

    override val definition: PacketDefinition = ProtocolPacketTable.SET_COLUMN

    override fun write(buf: WriteBuffer) {
        buf.writeInt(x)
        buf.writeInt(z)

        // Empty heightmap (32x32 entries set to Int.MIN_VALUE, matching LegacyHeightmap).
        repeat(HEIGHTMAP_ENTRIES) {
            buf.writeInt(Int.MIN_VALUE)
        }

        // Empty tintmap (IntBytePalette: count=1, keys[0]=0, byteLength=1280, zeroed bitfield).
        buf.writeVarInt(EMPTY_TINTMAP_BYTES)
        buf.writeShort(1)
        buf.writeInt(0)
        buf.writeInt(TINTMAP_BITFIELD_BYTES)
        buf.skip(TINTMAP_BITFIELD_BYTES)
    }

    override fun size(): Int = EMPTY_SIZE

    companion object {
        private const val HEIGHTMAP_ENTRIES: Int = 1024
        private const val TINTMAP_BITFIELD_BYTES: Int = 1280
        private const val EMPTY_TINTMAP_BYTES: Int = DataSize.SHORT + DataSize.INT * 2 + TINTMAP_BITFIELD_BYTES
        const val EMPTY_SIZE: Int = DataSize.INT * 2 + HEIGHTMAP_ENTRIES * DataSize.INT + 2 + EMPTY_TINTMAP_BYTES
    }
}
