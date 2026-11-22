package dev.sectile.network.protocol.entity.structures

import dev.sectile.network.api.buffer.ReadBuffer
import dev.sectile.network.api.buffer.WriteBuffer
import dev.sectile.network.api.buffer.readSlot
import dev.sectile.network.api.buffer.writeSlot
import dev.sectile.network.protocol.DataSize
import dev.sectile.network.protocol.ProtocolFormatException
import dev.sectile.network.protocol.entity.contracts.ComponentUpdate
import dev.sectile.network.protocol.entity.contracts.writeComponentUpdate

class EntityUpdate(
    var networkId: Int = 0,
    var removed: MutableList<Int>? = null,
    var updates: MutableList<ComponentUpdate>? = null,
) {

    fun write(buf: WriteBuffer) {
        var nullBits = 0
        if (removed != null) nullBits = nullBits or 0x01
        if (updates != null) nullBits = nullBits or 0x02

        buf.writeByte(nullBits)
        buf.writeInt(networkId)

        val removedSlot = buf.reserveOffset()
        val updatesSlot = buf.reserveOffset()

        val varBlockStart = buf.index

        buf.writeSlot(removedSlot, varBlockStart, removed) { list ->
            buf.writeVarInt(list.size)
            list.forEach { buf.writeByte(it) }
        }
        buf.writeSlot(updatesSlot, varBlockStart, updates) { list ->
            buf.writeVarInt(list.size)
            list.forEach { buf.writeComponentUpdate(it) }
        }
    }

    fun read(buf: ReadBuffer) {
        val base = buf.index
        val nullBits = buf.readUnsignedByte()
        networkId = buf.readInt()

        val varBlockStart = base + VARIABLE_BLOCK_START
        buf.skipTo(varBlockStart)

        removed = buf.readSlot(base + 5, varBlockStart, nullBits and 0x01 != 0, "Removed") {
            val count = buf.readVarInt()
            if (count < 0 || count > MAX_COMPONENTS) {
                throw ProtocolFormatException("Array too long in field 'Removed': $count")
            }
            val list = ArrayList<Int>(minOf(count, 64))
            repeat(count) { list.add(buf.readUnsignedByte()) }
            list
        }

        updates = buf.readSlot(base + 9, varBlockStart, nullBits and 0x02 != 0, "Updates") {
            val count = buf.readVarInt()
            if (count < 0 || count > MAX_COMPONENTS) {
                throw ProtocolFormatException("Array too long in field 'Updates': $count")
            }
            val list = ArrayList<ComponentUpdate>(minOf(count, 64))
            repeat(count) {
                val typeId = buf.readVarInt()
                list.add(UnknownComponentUpdate(typeId))
            }
            list
        }
    }

    fun computeSize(): Int {
        var size = VARIABLE_BLOCK_START
        removed?.let { size += DataSize.varListSize(it.size, DataSize.BYTE) }
        updates?.let { list ->
            size += DataSize.varListSize(list) { DataSize.varInt(it.typeId) + it.size() }
        }
        return size
    }

    companion object {
        const val VARIABLE_BLOCK_START: Int = 13
        const val MAX_COMPONENTS: Int = DataSize.MAX_ARRAY_LENGTH
    }
}
