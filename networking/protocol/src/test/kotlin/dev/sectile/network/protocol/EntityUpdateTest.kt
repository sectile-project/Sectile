package dev.sectile.network.protocol

import dev.sectile.network.api.packet.OutboundPacket
import dev.sectile.network.protocol.entities.EntityUpdate
import dev.sectile.network.protocol.entities.EntityUpdatesPacket
import dev.sectile.network.protocol.entities.MovementStatesComponentUpdate
import dev.sectile.network.protocol.entities.TransformComponentUpdate
import dev.sectile.network.protocol.structures.Direction
import dev.sectile.network.protocol.structures.ModelTransform
import dev.sectile.network.protocol.structures.MovementStates
import dev.sectile.network.protocol.structures.Position
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Verifies the `EntityUpdates` outbound packet layout against the build 258 serializer.
 */
class EntityUpdateTest {

    private fun serialize(packet: OutboundPacket): ByteArray {
        val buffer = TestWriteBuffer()
        packet.write(buffer)
        return buffer.toByteArray()
    }

    private fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    private fun transform(): TransformComponentUpdate = TransformComponentUpdate(
        ModelTransform(
            position = Position(1.0, 2.0, 3.0),
            bodyOrientation = Direction(0.1f, 0.2f, 0.3f),
            lookOrientation = Direction(0.4f, 0.5f, 0.6f),
        ),
    )

    @Test
    fun `the entity update matches the official serializer`() {
        val packet = EntityUpdatesPacket(
            updates = mutableListOf(
                EntityUpdate(
                    networkId = 1,
                    updates = mutableListOf(
                        transform(),
                        MovementStatesComponentUpdate(
                            MovementStates(idle = true, onGround = true),
                        ),
                    ),
                ),
            ),
        )

        assertEquals(
            "02ffffffff00000000010201000000ffffffff00000000020907000000000000f03f0000000000000040" +
                "0000000000000840cdcccc3dcdcc4c3e9a99993ecdcccc3e0000003f9a99193f0a01800000",
            hex(serialize(packet)),
        )
    }

    @Test
    fun `an entity without components matches the official serializer`() {
        val packet = EntityUpdatesPacket(
            updates = mutableListOf(EntityUpdate(networkId = 7)),
        )

        assertEquals(
            "02ffffffff00000000010007000000ffffffffffffffff",
            hex(serialize(packet)),
        )
    }

    @Test
    fun `the entity packet is compressed`() {
        val definition = EntityUpdatesPacket().definition
        assertTrue(definition.compressed)
        assertEquals(161, definition.id)
    }
}
