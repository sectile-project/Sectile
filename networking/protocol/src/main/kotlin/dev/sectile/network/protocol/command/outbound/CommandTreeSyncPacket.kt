package dev.sectile.network.protocol.command.outbound

import dev.sectile.network.api.buffer.WriteBuffer
import dev.sectile.network.api.packet.OutboundPacket
import dev.sectile.network.api.packet.PacketDefinition
import dev.sectile.network.protocol.DataSize
import dev.sectile.network.protocol.ProtocolPacketTable
import dev.sectile.network.protocol.command.structures.CommandTreeEntry
import dev.sectile.network.protocol.command.structures.checkCommandArrayBounds

class CommandTreeSyncPacket(
    var commands: List<CommandTreeEntry>? = null,
) : OutboundPacket {

    override val definition: PacketDefinition = ProtocolPacketTable.COMMAND_TREE_SYNC

    override fun write(buf: WriteBuffer) {
        val current = commands
        if (current == null) {
            buf.writeByte(0)
            return
        }
        buf.writeByte(0x01)
        checkCommandArrayBounds(current.size, "Commands")
        buf.writeVarInt(current.size)
        current.forEach { it.write(buf) }
    }

    override fun size(): Int = VARIABLE_BLOCK_START + (commands?.let { DataSize.varListSize(it, CommandTreeEntry::computeSize) } ?: 0)

    companion object {
        const val VARIABLE_BLOCK_START: Int = 1
    }
}
