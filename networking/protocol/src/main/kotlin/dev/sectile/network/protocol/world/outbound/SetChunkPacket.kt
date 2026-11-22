package dev.sectile.network.protocol.world.outbound

import dev.sectile.network.api.buffer.WriteBuffer
import dev.sectile.network.api.packet.OutboundPacket
import dev.sectile.network.api.packet.PacketDefinition
import dev.sectile.network.protocol.ProtocolPacketTable

/**
 * When `localLight`, `globalLight`, and `data` are omitted (`nullBits = 0x00`), the client marks
 * column `(x, z)` as empty (`IsEmpty = true`), finishing world load immediately instead of
 * waiting for the 30s timeout.
 */
class SetChunkPacket(
    var x: Int = 0,
    var y: Int = 0,
    var z: Int = 0,
) : OutboundPacket {

    override val definition: PacketDefinition = ProtocolPacketTable.SET_CHUNK

    override fun write(buf: WriteBuffer) {
        buf.writeByte(0x00)
        buf.writeInt(x)
        buf.writeInt(y)
        buf.writeInt(z)
        buf.writeInt(-1)
        buf.writeInt(-1)
        buf.writeInt(-1)
    }

    override fun size(): Int = EMPTY_SIZE

    companion object {
        const val EMPTY_SIZE: Int = 25
    }
}
