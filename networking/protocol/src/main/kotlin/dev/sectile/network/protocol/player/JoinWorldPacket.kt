package dev.sectile.network.protocol.player

import dev.sectile.network.api.buffer.WriteBuffer
import dev.sectile.network.api.packet.OutboundPacket
import dev.sectile.network.api.packet.PacketDefinition
import dev.sectile.network.protocol.ProtocolPacketTable
import java.util.UUID

/**
 * Outbound `JoinWorld` packet (id 104, server -> client).
 *
 * Directs the client to enter the target world.
 *
 * Fixed 26-byte layout: nullability bitmask (1 byte), flags (1 byte: `0x01` clear world,
 * `0x02` fade in/out), world UUID (16 bytes RFC 4122), and vertical world section bounds
 * (2 ints, zero-filled when absent).
 */
class JoinWorldPacket(
    var clearWorld: Boolean = true,
    var fadeInOut: Boolean = false,
    var worldUuid: UUID = UUID(0L, 0L),
    var minSectionY: Int? = null,
    var maxSectionY: Int? = null,
) : OutboundPacket {

    override val definition: PacketDefinition
        get() = ProtocolPacketTable.JOIN_WORLD

    override fun write(buf: WriteBuffer) {
        var nullBits = 0
        if (minSectionY != null) nullBits = nullBits or 0x01
        if (maxSectionY != null) nullBits = nullBits or 0x02
        buf.writeByte(nullBits)

        var flags = 0
        if (clearWorld) flags = flags or 0x01
        if (fadeInOut) flags = flags or 0x02
        buf.writeByte(flags)

        buf.writeUUID(worldUuid)

        // Bounds are optional, yet the fixed block always occupies 26 bytes.
        buf.writeInt(minSectionY ?: 0)
        buf.writeInt(maxSectionY ?: 0)
    }

    override fun size(): Int = ProtocolPacketTable.JOIN_WORLD.maxSize
}
