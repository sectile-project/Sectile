package dev.sectile.network.protocol

import dev.sectile.network.api.packet.OutboundPacket
import dev.sectile.network.protocol.assets.AssetTypes
import dev.sectile.network.protocol.assets.AssetUpdatePacket
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Verifies outbound asset update packets (`Update*`).
 *
 * The client aborts world loading if any of the 47 expected asset registries is missing, so
 * all 47 must be sent (most empty, plus default entries `Empty`/`Unknown`/`Default` where
 * indexed during startup). Expected byte sequences match the build 258 official serializer.
 */
class AssetUpdateTest {

    private fun serialize(packet: OutboundPacket): ByteArray {
        val buffer = TestWriteBuffer()
        packet.write(buffer)
        return buffer.toByteArray()
    }

    private fun serializeEmpty(packet: AssetUpdatePacket): ByteArray {
        val buffer = TestWriteBuffer()
        packet.writeEmpty(buffer)
        return buffer.toByteArray()
    }

    @Test
    fun `every required asset type is sent`() {
        assertEquals(47, AssetTypes.ALL.size)
        assertEquals(47, AssetTypes.ALL.map { it.definition.id }.distinct().size)
        assertTrue(AssetTypes.ALL.all { it.definition.compressed })
    }

    @Test
    fun `the empty asset shapes match the official serializer`() {
        val expected = mapOf(
            40 to "0100000000000000",
            41 to "01000000000000",
            42 to "01000000000000",
            43 to "01000000000000",
            44 to "010000",
            45 to "0000",
            46 to "010000",
            47 to "01000000000000",
            48 to "010000",
            49 to "010000000000ffffffff00",
            50 to "010000000000ffffffff00",
            51 to "01000000000000",
            52 to "010000",
            53 to "01000000000000",
            54 to "01000000000000ffffffff00",
            55 to "01000000000000",
            56 to "010000",
            57 to "01000000000000",
            58 to "010000",
            59 to "010000",
            60 to "010000000000ffffffff00",
            61 to "0100000000000000",
            62 to "01000000000000",
            63 to "01000000000000",
            64 to "010000",
            65 to "01000000000000",
            66 to "01000000000000",
            67 to "01000000000000",
            68 to "010000",
            72 to "01000000000000",
            73 to "01000000000000",
            74 to "01000000000000",
            75 to "01000000000000",
            79 to "01000000000000",
            80 to "01000000000000",
            81 to "01000000000000",
            82 to "01000000000000",
            83 to "01000000000000",
            84 to "01000000000000",
            87 to "01000000000000",
            88 to "01000000000000",
            89 to "01000000000000",
            90 to "010000",
            91 to "01000000000000",
            92 to "01000000000000",
            93 to "01000000000000",
            94 to "01000000000000",
        )

        for ((id, hex) in expected) {
            val packet = AssetTypes.ALL.firstOrNull { it.definition.id == id }
            assertNotNull(packet, "Missing asset type $id")
            val bytes = serializeEmpty(packet!!)
            val actual = bytes.joinToString("") { "%02x".format(it) }
            assertEquals(hex, actual, "Unexpected empty payload for packet $id")
        }
    }

    @Test
    fun `default preloaded asset packets match the official serializer`() {
        val hitboxes = serialize(AssetTypes.ALL.first { it.definition.id == 41 })
        assertEquals(
            "0100010000000100000000010000000000000000000000000000803f0000803f0000803f",
            hitboxes.joinToString("") { "%02x".format(it) },
        )

        val environments = serialize(AssetTypes.ALL.first { it.definition.id == 61 })
        assertEquals(
            "010001000000010100000000030a335500000000ffffffffffffffff07556e6b6e6f776e",
            environments.joinToString("") { "%02x".format(it) },
        )

        val categories = serialize(AssetTypes.ALL.first { it.definition.id == 56 })
        assertEquals(
            "0100010f000000000000000000080000001000000011000000ffffffff0744656661756c740744656661756c7400" +
                "010f000000000000000000080000001000000011000000ffffffff0744656661756c740744656661756c740000",
            categories.joinToString("") { "%02x".format(it) },
        )

        val weathers = serialize(AssetTypes.ALL.first { it.definition.id == 47 })
        assertEquals(145, weathers.size)

        val blockTypes = serialize(AssetTypes.ALL.first { it.definition.id == 40 })
        assertEquals(584, blockTypes.size)

        val fluids = serialize(AssetTypes.ALL.first { it.definition.id == 83 })
        assertEquals(123, fluids.size)
    }
}
