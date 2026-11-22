package dev.sectile.network.protocol.world.structures

import dev.sectile.network.api.buffer.ReadBuffer
import dev.sectile.network.api.buffer.WriteBuffer
import dev.sectile.network.protocol.DataSize
import dev.sectile.network.protocol.ProtocolFormatException

class WorldConfig(
    var allowBlockBreaking: Boolean = true,
    var blockBreakingBypassTagIndex: Int = TAG_NOT_FOUND,
    var allowBlockGathering: Boolean = true,
    var allowBlockPlacement: Boolean = true,
    var blockedRootInteractionTagIndexes: MutableList<Int>? = null,
) {

    fun write(buf: WriteBuffer) {
        buf.writeByte(if (blockedRootInteractionTagIndexes == null) 0 else 0x01)
        buf.writeBoolean(allowBlockBreaking)
        buf.writeInt(blockBreakingBypassTagIndex)
        var bits = 0
        if (allowBlockGathering) bits = bits or 0x01
        if (allowBlockPlacement) bits = bits or 0x02
        buf.writeByte(bits)

        blockedRootInteractionTagIndexes?.let { tags ->
            buf.writeVarInt(tags.size)
            tags.forEach { buf.writeInt(it) }
        }
    }

    fun read(buf: ReadBuffer) {
        val nullBits = buf.readUnsignedByte()
        allowBlockBreaking = buf.readBoolean()
        blockBreakingBypassTagIndex = buf.readInt()
        val bits = buf.readUnsignedByte()
        allowBlockGathering = bits and 0x01 != 0
        allowBlockPlacement = bits and 0x02 != 0

        blockedRootInteractionTagIndexes = if (nullBits and 0x01 != 0) {
            val count = buf.readVarInt()
            if (count < 0 || count > MAX_TAG_INDEXES) {
                throw ProtocolFormatException(
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

    fun computeSize(): Int = FIXED_SIZE + (blockedRootInteractionTagIndexes?.let { DataSize.varListSize(it.size, DataSize.INT) } ?: 0)

    companion object {
        const val FIXED_SIZE: Int = 7

        /** Sentinel value matching `Integer.MIN_VALUE` when no bypass tag is configured. */
        const val TAG_NOT_FOUND: Int = Int.MIN_VALUE
        const val MAX_TAG_INDEXES: Int = DataSize.MAX_ARRAY_LENGTH
        const val MAX_SIZE: Int = 16_384_012
    }
}
