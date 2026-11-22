package dev.sectile.network.protocol.connection.outbound

import dev.sectile.network.api.buffer.WriteBuffer
import dev.sectile.network.api.buffer.writeSlot
import dev.sectile.network.api.packet.OutboundPacket
import dev.sectile.network.api.packet.PacketDefinition
import dev.sectile.network.protocol.DataSize
import dev.sectile.network.protocol.ProtocolPacketTable
import dev.sectile.network.protocol.shared.HostAddress

class ServerInfoPacket(
    var serverName: String? = null,
    var motd: String? = null,
    var maxPlayers: Int = 0,
    var fallbackServer: HostAddress? = null,
) : OutboundPacket {

    override val definition: PacketDefinition = ProtocolPacketTable.SERVER_INFO

    override fun write(buf: WriteBuffer) {
        var nullBits = 0
        if (serverName != null) nullBits = nullBits or 0x01
        if (motd != null) nullBits = nullBits or 0x02
        if (fallbackServer != null) nullBits = nullBits or 0x04

        buf.writeByte(nullBits)
        buf.writeInt(maxPlayers)

        val serverNameSlot = buf.reserveOffset()
        val motdSlot = buf.reserveOffset()
        val fallbackSlot = buf.reserveOffset()

        val varBlockStart = buf.index

        buf.writeSlot(serverNameSlot, varBlockStart, serverName) {
            buf.writeVarString(it, MAX_STRING_LENGTH)
        }
        buf.writeSlot(motdSlot, varBlockStart, motd) {
            buf.writeVarString(it, MAX_STRING_LENGTH)
        }
        buf.writeSlot(fallbackSlot, varBlockStart, fallbackServer) {
            it.write(buf)
        }
    }

    override fun size(): Int {
        var size = VARIABLE_BLOCK_START
        serverName?.let { size += DataSize.varStringSize(it) }
        motd?.let { size += DataSize.varStringSize(it) }
        fallbackServer?.let { size += it.computeSize() }
        return size
    }

    companion object {
        const val VARIABLE_BLOCK_START: Int = 17
        const val MAX_STRING_LENGTH: Int = DataSize.MAX_STRING_LENGTH
    }
}
