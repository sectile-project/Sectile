package dev.sectile.network.protocol.asset.outbound

import dev.sectile.network.api.buffer.WriteBuffer
import dev.sectile.network.api.packet.OutboundPacket
import dev.sectile.network.api.packet.PacketDefinition

/**
 * Even an empty world must send all 47 expected asset type updates before `WorldLoadFinished`,
 * otherwise the client aborts world loading. Most are sent empty, except registries where the
 * client indexes default entries (`Empty`, `Unknown`, `Default`).
 */
class AssetUpdatePacket(
    override val definition: PacketDefinition,
    private val shape: Shape,
    private val payloadSize: Int = SIZE,
    private val payloadWriter: ((WriteBuffer) -> Unit)? = null,
) : OutboundPacket {

    enum class Shape {
        LIST,
        LIST_NO_NULLS,
        LIST_WITH_MAX_ID,
        LIST_WITH_MAX_ID_AND_FLAGS,
        LIST_WITH_SLOTS,
        LIST_WITH_FLAGS_AND_SLOTS,
    }

    override fun write(buf: WriteBuffer) {
        if (payloadWriter != null) {
            payloadWriter.invoke(buf)
            return
        }
        writeEmpty(buf)
    }

    fun writeEmpty(buf: WriteBuffer) {
        if (shape != Shape.LIST_NO_NULLS) {
            // Bit 0 marks the asset map/list as present even when empty.
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

        buf.writeVarInt(0)
    }

    private fun writeSlots(buf: WriteBuffer) {
        // First slot points to the start of the variable block; second marks removed assets absent.
        buf.writeInt(0)
        buf.writeInt(-1)
    }

    override fun size(): Int = payloadSize

    companion object {
        const val UPDATE_TYPE_INIT: Int = 0
        const val SIZE: Int = 12
    }
}
