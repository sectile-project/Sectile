package dev.sectile.network.protocol.connection

import dev.sectile.network.api.buffer.ReadBuffer
import dev.sectile.network.api.packet.InboundPacket
import dev.sectile.network.api.packet.PacketDefinition
import dev.sectile.network.protocol.ProtocolCodec
import dev.sectile.network.protocol.ProtocolPacketTable
import dev.sectile.network.protocol.structures.PlayerSkin
import java.util.UUID

/**
 * Inbound `InsecurePlayerOptions` packet (id 363, client -> server).
 *
 * Layout: nullability bitmask (1 byte), player UUID (16 bytes RFC 4122), two 4-byte offset slots
 * (`username` and `skin`), and the variable block.
 */
class InsecurePlayerOptionsPacket(
    var uuid: UUID = UUID(0L, 0L),
    var username: String = "",
    var skin: PlayerSkin? = null,
) : InboundPacket {

    override val definition: PacketDefinition
        get() = ProtocolPacketTable.INSECURE_PLAYER_OPTIONS

    override fun read(buf: ReadBuffer) {
        val base = buf.index
        val nullBits = buf.readUnsignedByte()
        uuid = buf.readUUID()

        // Offset slots occupy `base + 17` through `base + 24`.
        val varBlockStart = base + VARIABLE_BLOCK_START
        buf.skipTo(varBlockStart)

        ProtocolCodec.requireSlot(buf, base + 17, buf.index - varBlockStart, "Username")
        username = ProtocolCodec.readVarAsciiString(buf, MAX_USERNAME_LENGTH, "Username")

        if (nullBits and 0x01 != 0) {
            ProtocolCodec.requireSlot(buf, base + 21, buf.index - varBlockStart, "Skin")
            skin = PlayerSkin().also { it.read(buf) }
        } else {
            ProtocolCodec.requireSlot(buf, base + 21, -1, "Skin")
            skin = null
        }
    }

    companion object {
        /** Fixed block length in bytes. */
        const val FIXED_BLOCK_SIZE: Int = 17

        /** Start offset of the variable block. */
        const val VARIABLE_BLOCK_START: Int = 25

        /** Maximum username string length. */
        const val MAX_USERNAME_LENGTH: Int = 16

        /** Maximum serialized size of the player skin structure. */
        const val MAX_SKIN_SIZE: Int = 2_103
    }
}
