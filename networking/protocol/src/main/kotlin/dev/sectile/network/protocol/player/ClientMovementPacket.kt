package dev.sectile.network.protocol.player

import dev.sectile.network.api.buffer.ReadBuffer
import dev.sectile.network.api.packet.InboundPacket
import dev.sectile.network.api.packet.PacketDefinition
import dev.sectile.network.protocol.ProtocolPacketTable
import dev.sectile.network.protocol.structures.Direction
import dev.sectile.network.protocol.structures.HalfFloatPosition
import dev.sectile.network.protocol.structures.MovementStates
import dev.sectile.network.protocol.structures.Position
import dev.sectile.network.protocol.structures.TeleportAck
import dev.sectile.network.protocol.structures.Vector3d

/**
 * Inbound `ClientMovement` packet (id 108, client -> server).
 *
 * Player movement snapshot with a fixed 117-byte payload:
 *  - nullability bitmask (2 bytes),
 *  - movement states (4 bytes, offset 2),
 *  - relative position (6 bytes, offset 6),
 *  - absolute position (24 bytes, offset 12),
 *  - body orientation (12 bytes, offset 36),
 *  - look orientation (12 bytes, offset 48),
 *  - teleport acknowledgement (1 byte, offset 60),
 *  - wish movement (24 bytes, offset 61),
 *  - velocity (24 bytes, offset 85),
 *  - mounted entity id (int, offset 109),
 *  - rider movement states (4 bytes, offset 113).
 */
class ClientMovementPacket(
    var movementStates: MovementStates? = null,
    var relativePosition: HalfFloatPosition? = null,
    var absolutePosition: Position? = null,
    var bodyOrientation: Direction? = null,
    var lookOrientation: Direction? = null,
    var teleportAck: TeleportAck? = null,
    var wishMovement: Position? = null,
    var velocity: Vector3d? = null,
    var mountedTo: Int = 0,
    var riderMovementStates: MovementStates? = null,
) : InboundPacket {

    override val definition: PacketDefinition
        get() = ProtocolPacketTable.CLIENT_MOVEMENT

    override fun read(buf: ReadBuffer) {
        val nullBits0 = buf.readUnsignedByte()
        val nullBits1 = buf.readUnsignedByte()

        movementStates = if (nullBits0 and 0x01 != 0) MovementStates().also { it.read(buf) } else null
        relativePosition = if (nullBits0 and 0x02 != 0) HalfFloatPosition().also { it.read(buf) } else null
        absolutePosition = if (nullBits0 and 0x04 != 0) Position().also { it.read(buf) } else null
        bodyOrientation = if (nullBits0 and 0x08 != 0) Direction().also { it.read(buf) } else null
        lookOrientation = if (nullBits0 and 0x10 != 0) Direction().also { it.read(buf) } else null
        teleportAck = if (nullBits0 and 0x20 != 0) TeleportAck().also { it.read(buf) } else null
        wishMovement = if (nullBits0 and 0x40 != 0) Position().also { it.read(buf) } else null
        velocity = if (nullBits0 and 0x80 != 0) Vector3d().also { it.read(buf) } else null
        mountedTo = buf.readInt()
        riderMovementStates = if (nullBits1 and 0x01 != 0) MovementStates().also { it.read(buf) } else null
    }
}
