package dev.sectile.network.protocol.structures

import dev.sectile.network.api.buffer.ReadBuffer
import dev.sectile.network.api.buffer.WriteBuffer
import dev.sectile.network.protocol.VarInt

/**
 * World rule configuration (`WorldConfig` of the Hytale protocol).
 *
 * Fixed 7-byte block (null map, block breaking, tag index, Gather/Place map) and,
 * optionally, the list of blocked root interaction tags.
 */
class WorldConfig(
    var allowBlockBreaking: Boolean = true,
    var blockBreakingBypassTagIndex: Int = TAG_NOT_FOUND,
    var allowBlockGathering: Boolean = true,
    var allowBlockPlacement: Boolean = true,
    var blockedRootInteractionTagIndexes: MutableList<Int>? = null,
) {

    fun write(buf: WriteBuffer) {
        buf.writeByte(if (blockedRootInteractionTagIndexes == null) 0 else 0x01)
        buf.writeByte(if (allowBlockBreaking) 1 else 0)
        buf.writeInt(blockBreakingBypassTagIndex)
        var bits = 0
        if (allowBlockGathering) bits = bits or 0x01
        if (allowBlockPlacement) bits = bits or 0x02
        buf.writeByte(bits)

        blockedRootInteractionTagIndexes?.let { tags ->
            VarInt.write(buf, tags.size)
            tags.forEach { buf.writeInt(it) }
        }
    }

    fun read(buf: ReadBuffer) {
        val nullBits = buf.readUnsignedByte()
        allowBlockBreaking = buf.readUnsignedByte() != 0
        blockBreakingBypassTagIndex = buf.readInt()
        val bits = buf.readUnsignedByte()
        allowBlockGathering = bits and 0x01 != 0
        allowBlockPlacement = bits and 0x02 != 0

        blockedRootInteractionTagIndexes = if (nullBits and 0x01 != 0) {
            val count = VarInt.read(buf)
            if (count < 0 || count > MAX_TAG_INDEXES) {
                throw dev.sectile.network.protocol.ProtocolFormatException(
                    "Array too long in field 'BlockedRootInteractionTagIndexes': $count",
                )
            }
            val list = ArrayList<Int>(minOf(count, 64))
            repeat(count) { list.add(buf.readInt()) }
            list
        } else {
            null
        }
    }

    companion object {
        /** Value used by the original server when there is no tag (`Integer.MIN_VALUE`). */
        const val TAG_NOT_FOUND: Int = -2147483648

        /** Maximum number of blocked tags. */
        const val MAX_TAG_INDEXES: Int = 4_096_000

        /** Maximum size of the serialized structure. */
        const val MAX_SIZE: Int = 16_384_012
    }
}
