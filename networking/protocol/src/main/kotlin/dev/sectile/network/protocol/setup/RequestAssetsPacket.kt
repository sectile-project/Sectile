package dev.sectile.network.protocol.setup

import dev.sectile.network.api.buffer.ReadBuffer
import dev.sectile.network.api.packet.InboundPacket
import dev.sectile.network.api.packet.PacketDefinition
import dev.sectile.network.protocol.ProtocolFormatException
import dev.sectile.network.protocol.ProtocolPacketTable
import dev.sectile.network.protocol.VarInt
import dev.sectile.network.protocol.structures.Asset

/**
 * Inbound `RequestAssets` packet (id 23, client -> server, Zstd compressed).
 *
 * Client request listing the missing assets it needs from the server.
 */
class RequestAssetsPacket(
    var assets: MutableList<Asset>? = null,
) : InboundPacket {

    override val definition: PacketDefinition
        get() = ProtocolPacketTable.REQUEST_ASSETS

    override fun read(buf: ReadBuffer) {
        val nullBits = buf.readUnsignedByte()
        assets = if (nullBits and 0x01 != 0) {
            val count = VarInt.read(buf)
            if (count < 0 || count > MAX_ASSETS) {
                throw ProtocolFormatException("Array too long in field 'Assets': $count")
            }
            val list = ArrayList<Asset>(minOf(count, 64))
            repeat(count) { list.add(Asset().also { it.read(buf) }) }
            list
        } else {
            null
        }
    }

    companion object {
        /** Maximum number of requested assets. */
        const val MAX_ASSETS: Int = 4_096_000
    }
}
