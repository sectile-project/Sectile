package dev.sectile.network.protocol.asset.inbound

import dev.sectile.network.api.buffer.ReadBuffer
import dev.sectile.network.api.packet.InboundPacket
import dev.sectile.network.api.packet.PacketDefinition
import dev.sectile.network.protocol.DataSize
import dev.sectile.network.protocol.ProtocolFormatException
import dev.sectile.network.protocol.ProtocolPacketTable
import dev.sectile.network.protocol.asset.structures.Asset

class RequestAssetsPacket(
    var assets: MutableList<Asset>? = null,
) : InboundPacket {

    override val definition: PacketDefinition = ProtocolPacketTable.REQUEST_ASSETS

    override fun read(buf: ReadBuffer) {
        val nullBits = buf.readUnsignedByte()
        assets = if (nullBits and 0x01 != 0) {
            val count = buf.readVarInt()
            if (count !in 0..MAX_ASSETS) {
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
        const val MAX_ASSETS: Int = DataSize.MAX_ARRAY_LENGTH
    }
}
