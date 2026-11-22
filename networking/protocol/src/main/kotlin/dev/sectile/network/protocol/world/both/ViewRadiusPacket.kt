package dev.sectile.network.protocol.world.both

import dev.sectile.network.api.buffer.ReadBuffer
import dev.sectile.network.api.buffer.WriteBuffer
import dev.sectile.network.api.packet.Packet
import dev.sectile.network.api.packet.PacketDefinition
import dev.sectile.network.protocol.ProtocolFormatException
import dev.sectile.network.protocol.ProtocolPacketTable

class ViewRadiusPacket(
    var value: Int = 0,
) : Packet {

    override val definition: PacketDefinition = ProtocolPacketTable.VIEW_RADIUS

    override fun write(buf: WriteBuffer) {
        buf.writeInt(value)
    }

    override fun read(buf: ReadBuffer) {
        value = buf.readInt()
        if (value < 0) throw ProtocolFormatException("Value below minimum: $value")
    }

    override fun size(): Int = ProtocolPacketTable.VIEW_RADIUS.maxSize
}
