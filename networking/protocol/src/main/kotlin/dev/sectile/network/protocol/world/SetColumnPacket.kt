package dev.sectile.network.protocol.world

import dev.sectile.network.api.buffer.WriteBuffer
import dev.sectile.network.api.packet.OutboundPacket
import dev.sectile.network.api.packet.PacketDefinition
import dev.sectile.network.protocol.ProtocolPacketTable
import dev.sectile.network.protocol.VarInt

/**
 * Outbound `SetColumn` packet (id 138, server -> client, Zstd compressed, chunks channel).
 *
 * Initializes column `(x, z)` with its `heightmap` (32x32 ints) and serialized `tintmap`
 * (`IntBytePalette`). Must be sent before any `SetChunk` packet in the same column.
 */
class SetColumnPacket(
    var x: Int = 0,
    var z: Int = 0,
) : OutboundPacket {

    override val definition: PacketDefinition
        get() = ProtocolPacketTable.SET_COLUMN

    override fun write(buf: WriteBuffer) {
        buf.writeInt(x)
        buf.writeInt(z)

        // Empty heightmap (32x32 = 1024 entries set to Int.MIN_VALUE, matching LegacyHeightmap).
        repeat(HEIGHTMAP_ENTRIES) {
            buf.writeInt(Int.MIN_VALUE)
        }

        // Empty tintmap (IntBytePalette: count=1, keys[0]=0, byteLength=1280, zeroed bitfield).
        VarInt.write(buf, EMPTY_TINTMAP_BYTES)
        buf.writeShort(1)
        buf.writeInt(0)
        buf.writeInt(TINTMAP_BITFIELD_BYTES)
        buf.skip(TINTMAP_BITFIELD_BYTES)
    }

    override fun size(): Int = EMPTY_SIZE

    companion object {
        private const val HEIGHTMAP_ENTRIES: Int = 1024
        private const val TINTMAP_BITFIELD_BYTES: Int = 1280
        private const val EMPTY_TINTMAP_BYTES: Int = 2 + 4 + 4 + TINTMAP_BITFIELD_BYTES

        /** Uncompressed empty column size: 4104 fixed + 2 (VarInt) + 1290 = 5396 bytes. */
        const val EMPTY_SIZE: Int = 4104 + 2 + EMPTY_TINTMAP_BYTES
    }
}
