package dev.sectile.network.protocol.connection.inbound

import dev.sectile.network.api.buffer.ReadBuffer
import dev.sectile.network.api.buffer.readSlot
import dev.sectile.network.api.packet.InboundPacket
import dev.sectile.network.api.packet.PacketDefinition
import dev.sectile.network.protocol.ProtocolPacketTable
import dev.sectile.network.protocol.player.structures.PlayerSkin
import java.util.UUID

class InsecurePlayerOptionsPacket(
    var uuid: UUID = UUID(0L, 0L),
    var username: String = "",
    var skin: PlayerSkin? = null,
) : InboundPacket {

    override val definition: PacketDefinition = ProtocolPacketTable.INSECURE_PLAYER_OPTIONS

    override fun read(buf: ReadBuffer) {
        val base = buf.index
        val nullBits = buf.readUnsignedByte()
        uuid = buf.readUUID()

        val varBlockStart = base + VARIABLE_BLOCK_START
        buf.skipTo(varBlockStart)

        buf.requireSlot(base + 17, buf.index - varBlockStart, "Username")
        username = buf.readVarAsciiString(MAX_USERNAME_LENGTH, "Username")

        skin = buf.readSlot(base + 21, varBlockStart, nullBits and 0x01 != 0, "Skin") {
            PlayerSkin().also { it.read(buf) }
        }
    }

    companion object {
        const val FIXED_BLOCK_SIZE: Int = 17
        const val VARIABLE_BLOCK_START: Int = 25
        const val MAX_USERNAME_LENGTH: Int = 16
        const val MAX_SKIN_SIZE: Int = 2_103
    }
}
