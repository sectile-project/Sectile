package dev.sectile.network.protocol.entities

import dev.sectile.network.api.buffer.ReadBuffer
import dev.sectile.network.api.buffer.WriteBuffer
import dev.sectile.network.protocol.ProtocolCodec
import dev.sectile.network.protocol.ProtocolFormatException
import dev.sectile.network.protocol.VarInt

/**
 * Update of an entity (`EntityUpdate` of the Hytale protocol).
 *
 * Layout: nullability bitmask (1 byte), the entity's network id (LE int), 2 slots of
 * 4-byte offsets, and the variable block with the removed and updated components.
 */
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

        val removedSlot = ProtocolCodec.reserveOffset(buf)
        val updatesSlot = ProtocolCodec.reserveOffset(buf)

        val varBlockStart = buf.index

        if (removed == null) {
            buf.writeIntAt(removedSlot, -1)
        } else {
            buf.writeIntAt(removedSlot, buf.index - varBlockStart)
            VarInt.write(buf, removed!!.size)
            removed!!.forEach { buf.writeByte(it) }
        }

        if (updates == null) {
            buf.writeIntAt(updatesSlot, -1)
        } else {
            buf.writeIntAt(updatesSlot, buf.index - varBlockStart)
            VarInt.write(buf, updates!!.size)
            updates!!.forEach {
                buf.writeComponentUpdate(it)
            }
        }
    }

    fun read(buf: ReadBuffer) {
        val base = buf.index
        val nullBits = buf.readUnsignedByte()
        networkId = buf.readInt()

        val varBlockStart = base + VARIABLE_BLOCK_START
        buf.skipTo(varBlockStart)

        removed = if (nullBits and 0x01 != 0) {
            ProtocolCodec.requireSlot(buf, base + 5, buf.index - varBlockStart, "Removed")
            val count = VarInt.read(buf)
            if (count < 0 || count > MAX_COMPONENTS) {
                throw ProtocolFormatException("Array too long in field 'Removed': $count")
            }
            val list = ArrayList<Int>(minOf(count, 64))
            repeat(count) { list.add(buf.readUnsignedByte()) }
            list
        } else {
            ProtocolCodec.requireSlot(buf, base + 5, -1, "Removed")
            null
        }

        updates = if (nullBits and 0x02 != 0) {
            ProtocolCodec.requireSlot(buf, base + 9, buf.index - varBlockStart, "Updates")
            val count = VarInt.read(buf)
            if (count < 0 || count > MAX_COMPONENTS) {
                throw ProtocolFormatException("Array too long in field 'Updates': $count")
            }
            val list = ArrayList<ComponentUpdate>(minOf(count, 64))
            repeat(count) {
                val typeId = VarInt.read(buf)
                list.add(UnknownComponentUpdate(typeId))
            }
            list
        } else {
            ProtocolCodec.requireSlot(buf, base + 9, -1, "Updates")
            null
        }
    }

    /** Size of the payload. */
    fun computeSize(): Int {
        var size = VARIABLE_BLOCK_START
        removed?.let { size += VarInt.sizeOf(it.size) + it.size }
        updates?.let { list ->
            size += VarInt.sizeOf(list.size)
            list.forEach { size += VarInt.sizeOf(it.typeId) + it.size() }
        }
        return size
    }

    companion object {
        /** Position of the variable block. */
        const val VARIABLE_BLOCK_START: Int = 13

        /** Maximum number of components. */
        const val MAX_COMPONENTS: Int = 4_096_000
    }
}

/**
 * Update of a component that the server does not know how to read: it only stores its id.
 *
 * The server does not receive entity updates, so this class exists only
 * to be able to consume the payload.
 */
class UnknownComponentUpdate(override val typeId: Int) : ComponentUpdate {

    override fun write(buf: WriteBuffer) = Unit

    override fun size(): Int = 0
}
