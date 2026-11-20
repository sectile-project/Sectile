package dev.sectile.network.protocol.assets

import dev.sectile.network.api.buffer.WriteBuffer
import dev.sectile.network.api.packet.OutboundPacket
import dev.sectile.network.api.packet.PacketDefinition
import dev.sectile.network.protocol.VarInt

/**
 * Outbound packet for asset type updates (`Update*`, packet IDs 40-94).
 *
 * Even an empty world must send all 47 expected asset type updates before `WorldLoadFinished`,
 * otherwise the client aborts world loading. Most are sent empty, except a few registries where
 * the client indexes default entries (`Empty`, `Unknown`, `Default`).
 *
 * All of these packets are Zstd-compressed (as defined in their [PacketDefinition]). An empty
 * payload layout consists of:
 *  - nullability bitmask (1 byte), except on packets without nullable fields,
 *  - update type (1 byte, always `Init`),
 *  - fixed header fields (`maxId`, `flags`, etc.),
 *  - offset slots, if any,
 *  - entry count (VarInt `0`).
 */
class AssetUpdatePacket(
    override val definition: PacketDefinition,
    private val shape: Shape,
    private val payloadSize: Int = SIZE,
    private val payloadWriter: ((WriteBuffer) -> Unit)? = null,
) : OutboundPacket {

    /** Binary layout shape of an asset update packet payload. */
    enum class Shape {
        /** Nullability bitmask, update type, and entry count. */
        LIST,

        /** Update type and entry count without nullability bitmask. */
        LIST_NO_NULLS,

        /** Nullability bitmask, update type, `maxId`, and entry count. */
        LIST_WITH_MAX_ID,

        /** Nullability bitmask, update type, `maxId`, flags, and entry count. */
        LIST_WITH_MAX_ID_AND_FLAGS,

        /** Nullability bitmask, update type, two offset slots, and entry count. */
        LIST_WITH_SLOTS,

        /** Nullability bitmask, update type, flags, two offset slots, and entry count. */
        LIST_WITH_FLAGS_AND_SLOTS,
    }

    override fun write(buf: WriteBuffer) {
        if (payloadWriter != null) {
            payloadWriter.invoke(buf)
            return
        }
        writeEmpty(buf)
    }

    /** Writes an empty init payload matching this packet's [shape]. */
    fun writeEmpty(buf: WriteBuffer) {
        if (shape != Shape.LIST_NO_NULLS) {
            // Bit 0: asset map/list is present (even when empty).
            buf.writeByte(0x01)
        }
        buf.writeByte(UPDATE_TYPE_INIT)

        when (shape) {
            Shape.LIST, Shape.LIST_NO_NULLS -> Unit

            Shape.LIST_WITH_MAX_ID -> buf.writeInt(0)

            Shape.LIST_WITH_MAX_ID_AND_FLAGS -> {
                buf.writeInt(0)
                buf.writeByte(0)
            }

            Shape.LIST_WITH_SLOTS -> writeSlots(buf)

            Shape.LIST_WITH_FLAGS_AND_SLOTS -> {
                buf.writeByte(0)
                writeSlots(buf)
            }
        }

        // Variable block: zero entries.
        VarInt.write(buf, 0)
    }

    /**
     * Two offset slots: the first points directly to the start of the variable block (`0`),
     * and the second marks the removed-assets list as absent (`-1`).
     */
    private fun writeSlots(buf: WriteBuffer) {
        buf.writeInt(0)
        buf.writeInt(-1)
    }

    override fun size(): Int = payloadSize

    companion object {
        /** `Init` update type: full initial asset snapshot. */
        const val UPDATE_TYPE_INIT: Int = 0

        /** Default payload size hint for empty asset packets. */
        const val SIZE: Int = 12
    }
}
