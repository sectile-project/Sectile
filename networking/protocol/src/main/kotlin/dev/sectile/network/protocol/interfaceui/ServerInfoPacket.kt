package dev.sectile.network.protocol.interfaceui

import dev.sectile.network.api.buffer.WriteBuffer
import dev.sectile.network.api.packet.OutboundPacket
import dev.sectile.network.api.packet.PacketDefinition
import dev.sectile.network.protocol.ProtocolCodec
import dev.sectile.network.protocol.ProtocolPacketTable
import dev.sectile.network.protocol.VarInt
import dev.sectile.network.protocol.structures.HostAddress

/**
 * Outbound `ServerInfo` packet (id 223, server -> client).
 *
 * Layout: nullability bitmask (1 byte), max players (int LE), three 4-byte offset slots,
 * and the variable block containing `serverName`, `motd`, and `fallbackServer`.
 */
class ServerInfoPacket(
    var serverName: String? = null,
    var motd: String? = null,
    var maxPlayers: Int = 0,
    var fallbackServer: HostAddress? = null,
) : OutboundPacket {

    override val definition: PacketDefinition
        get() = ProtocolPacketTable.SERVER_INFO

    override fun write(buf: WriteBuffer) {
        var nullBits = 0
        if (serverName != null) nullBits = nullBits or 0x01
        if (motd != null) nullBits = nullBits or 0x02
        if (fallbackServer != null) nullBits = nullBits or 0x04

        buf.writeByte(nullBits)
        buf.writeInt(maxPlayers)

        val serverNameSlot = ProtocolCodec.reserveOffset(buf)
        val motdSlot = ProtocolCodec.reserveOffset(buf)
        val fallbackSlot = ProtocolCodec.reserveOffset(buf)

        val varBlockStart = buf.index

        if (serverName == null) {
            buf.writeIntAt(serverNameSlot, -1)
        } else {
            buf.writeIntAt(serverNameSlot, buf.index - varBlockStart)
            ProtocolCodec.writeVarString(buf, serverName!!, MAX_STRING_LENGTH)
        }

        if (motd == null) {
            buf.writeIntAt(motdSlot, -1)
        } else {
            buf.writeIntAt(motdSlot, buf.index - varBlockStart)
            ProtocolCodec.writeVarString(buf, motd!!, MAX_STRING_LENGTH)
        }

        if (fallbackServer == null) {
            buf.writeIntAt(fallbackSlot, -1)
        } else {
            buf.writeIntAt(fallbackSlot, buf.index - varBlockStart)
            fallbackServer!!.write(buf)
        }
    }

    override fun size(): Int {
        var size = VARIABLE_BLOCK_START
        serverName?.let {
            val bytes = it.toByteArray(Charsets.UTF_8).size
            size += VarInt.sizeOf(bytes) + bytes
        }
        motd?.let {
            val bytes = it.toByteArray(Charsets.UTF_8).size
            size += VarInt.sizeOf(bytes) + bytes
        }
        fallbackServer?.let {
            val bytes = it.host.toByteArray(Charsets.UTF_8).size
            size += 2 + VarInt.sizeOf(bytes) + bytes
        }
        return size
    }

    companion object {
        /** Start offset of the variable block. */
        const val VARIABLE_BLOCK_START: Int = 17

        /** Maximum string length for server name and MOTD. */
        const val MAX_STRING_LENGTH: Int = 4_096_000
    }
}
