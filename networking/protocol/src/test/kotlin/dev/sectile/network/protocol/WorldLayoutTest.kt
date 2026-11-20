package dev.sectile.network.protocol

import dev.sectile.network.api.packet.OutboundPacket
import dev.sectile.network.protocol.enums.ArmorVisibilityOption
import dev.sectile.network.protocol.enums.ClientFeature
import dev.sectile.network.protocol.enums.GameMode
import dev.sectile.network.protocol.interfaceui.ServerInfoPacket
import dev.sectile.network.protocol.player.ClientMovementPacket
import dev.sectile.network.protocol.player.ClientReadyPacket
import dev.sectile.network.protocol.player.JoinWorldPacket
import dev.sectile.network.protocol.player.SetClientIdPacket
import dev.sectile.network.protocol.player.SetEntitySeedPacket
import dev.sectile.network.protocol.player.SetGameModePacket
import dev.sectile.network.protocol.setup.AssetFinalizePacket
import dev.sectile.network.protocol.setup.AssetInitializePacket
import dev.sectile.network.protocol.setup.RequestAssetsPacket
import dev.sectile.network.protocol.setup.ServerTagsPacket
import dev.sectile.network.protocol.setup.SetTimeDilationPacket
import dev.sectile.network.protocol.setup.SetUpdateRatePacket
import dev.sectile.network.protocol.setup.SetupFinalizePacket
import dev.sectile.network.protocol.setup.UpdateFeaturesPacket
import dev.sectile.network.protocol.setup.ViewRadiusPacket
import dev.sectile.network.protocol.setup.WorldLoadFinishedPacket
import dev.sectile.network.protocol.setup.WorldLoadProgressPacket
import dev.sectile.network.protocol.setup.WorldSettingsPacket
import dev.sectile.network.protocol.structures.Asset
import dev.sectile.network.protocol.structures.CombatConfig
import dev.sectile.network.protocol.structures.Direction
import dev.sectile.network.protocol.structures.HalfFloatPosition
import dev.sectile.network.protocol.structures.MovementStates
import dev.sectile.network.protocol.structures.PlayerConfig
import dev.sectile.network.protocol.structures.Position
import dev.sectile.network.protocol.structures.TeleportAck
import dev.sectile.network.protocol.structures.Vector3d
import dev.sectile.network.protocol.structures.WorldConfig
import dev.sectile.network.protocol.world.SetChunkPacket
import dev.sectile.network.protocol.world.UpdatePostFxSettingsPacket
import dev.sectile.network.protocol.world.UpdateSunSettingsPacket
import dev.sectile.network.protocol.world.UpdateTimePacket
import dev.sectile.network.protocol.world.UpdateTimeSettingsPacket
import dev.sectile.network.protocol.world.UpdateWeatherPacket
import dev.sectile.network.protocol.world.UpdateWorldMapSettingsPacket
import dev.sectile.network.protocol.world.WorldGameplayConfigPacket
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * Verifies wire layouts for world setup and gameplay packets.
 */
class WorldLayoutTest {

    private fun serialize(packet: OutboundPacket): ByteArray {
        val buffer = TestWriteBuffer()
        packet.write(buffer)
        return buffer.toByteArray()
    }

    private inline fun wireBytes(block: (TestWriteBuffer) -> Unit): ByteArray {
        val buffer = TestWriteBuffer()
        block(buffer)
        return buffer.toByteArray()
    }

    private fun readIntLE(bytes: ByteArray, offset: Int): Int {
        var value = 0
        for (shift in 0 until 4) {
            value = value or ((bytes[offset + shift].toInt() and 0xFF) shl (shift * 8))
        }
        return value
    }

    @Test
    fun `world settings carries the required assets inline`() {
        val emptyBytes = serialize(WorldSettingsPacket(mutableListOf()))
        assertEquals(0x01, emptyBytes[0].toInt() and 0xFF)
        assertEquals(0, emptyBytes[1].toInt())

        val withAsset = WorldSettingsPacket(mutableListOf(Asset("hash", "name")))
        val assetBytes = serialize(withAsset)
        assertEquals(0x01, assetBytes[0].toInt() and 0xFF)
        assertEquals(1, assetBytes[1].toInt())
        assertEquals("hash", String(assetBytes, 2, 4, Charsets.US_ASCII))
    }

    @Test
    fun `request assets is parsed`() {
        val emptyPacket = RequestAssetsPacket()
        emptyPacket.read(TestReadBuffer(byteArrayOf(0x01, 0x00)))
        assertEquals(0, emptyPacket.assets!!.size)

        val assetBytes = wireBytes { buf ->
            buf.writeByte(0x01)
            VarInt.write(buf, 1)
            Asset("hash", "name").write(buf)
        }
        val packet = RequestAssetsPacket()
        packet.read(TestReadBuffer(assetBytes))
        assertEquals(1, packet.assets!!.size)
        assertEquals("hash", packet.assets!![0].hash)
        assertEquals("name", packet.assets!![0].name)
    }

    @Test
    fun `world load progress and finished`() {
        val progress = WorldLoadProgressPacket(null, 50, 10)
        val bytes = serialize(progress)
        assertEquals(0x00, bytes[0].toInt() and 0xFF)
        assertEquals(50, readIntLE(bytes, 1))
        assertEquals(10, readIntLE(bytes, 5))

        assertEquals(0, serialize(WorldLoadFinishedPacket()).size)
        assertEquals(0, serialize(AssetFinalizePacket()).size)
        SetupFinalizePacket().read(TestReadBuffer(ByteArray(0)))
    }

    @Test
    fun `server info uses offset slots`() {
        val packet = ServerInfoPacket("server", "motd", 42, null)
        val bytes = serialize(packet)

        assertEquals(0x03, bytes[0].toInt() and 0xFF)
        assertEquals(42, readIntLE(bytes, 1))
        assertEquals(0, readIntLE(bytes, 5))
        assertEquals(7, readIntLE(bytes, 9))
        assertEquals(-1, readIntLE(bytes, 13))

        val reader = TestReadBuffer(bytes).also { it.skipTo(ServerInfoPacket.VARIABLE_BLOCK_START) }
        assertEquals("server", ProtocolCodec.readVarString(reader, ServerInfoPacket.MAX_STRING_LENGTH, "serverName"))
        assertEquals("motd", ProtocolCodec.readVarString(reader, ServerInfoPacket.MAX_STRING_LENGTH, "motd"))
    }

    @Test
    fun `player setup packets`() {
        val clientId = serialize(SetClientIdPacket(7))
        assertEquals(4, clientId.size)
        assertEquals(7, readIntLE(clientId, 0))

        val gameMode = serialize(SetGameModePacket(GameMode.CREATIVE))
        assertEquals(1, gameMode.size)
        assertEquals(1, gameMode[0].toInt())

        val worldUuid = UUID(1L, 2L)
        val join = serialize(JoinWorldPacket(true, false, worldUuid))
        // Build 258: nullBits (1) + flags (1) + UUID (16) + section bounds (8) = 26 bytes.
        assertEquals(26, join.size)
        assertEquals(0x00, join[0].toInt() and 0xFF)
        assertEquals(0x01, join[1].toInt() and 0xFF)
        assertEquals(worldUuid, TestReadBuffer(join).also { it.skipTo(2) }.readUUID())
        assertEquals(0, readIntLE(join, 18))
        assertEquals(0, readIntLE(join, 22))

        val withLimits = serialize(JoinWorldPacket(true, false, worldUuid, -4, 12))
        assertEquals(26, withLimits.size)
        assertEquals(0x03, withLimits[0].toInt() and 0xFF)
        assertEquals(-4, readIntLE(withLimits, 18))
        assertEquals(12, readIntLE(withLimits, 22))

        val ready = ClientReadyPacket()
        ready.read(TestReadBuffer(byteArrayOf(0x03)))
        assertTrue(ready.readyForChunks)
        assertTrue(ready.readyForGameplay)
    }

    @Test
    fun `client movement has a fixed layout`() {
        val bytes = wireBytes { buf ->
            buf.writeByte(0xFF)
            buf.writeByte(0x00)
            MovementStates(idle = true, onGround = true).write(buf)
            HalfFloatPosition(1, 2, 3).write(buf)
            Position(1.5, 2.5, 3.5).write(buf)
            Direction(0.1f, 0.2f, 0.3f).write(buf)
            Direction(0.4f, 0.5f, 0.6f).write(buf)
            TeleportAck(9).write(buf)
            Position(0.5, 0.0, 0.0).write(buf)
            Vector3d(0.25, 0.0, 0.0).write(buf)
            buf.writeInt(3)
            buf.skip(MovementStates.SIZE)
        }
        assertEquals(117, bytes.size)
        assertEquals(0xFF, bytes[0].toInt() and 0xFF)

        val read = ClientMovementPacket()
        read.read(TestReadBuffer(bytes))
        assertTrue(read.movementStates!!.idle)
        assertTrue(read.movementStates!!.onGround)
        assertEquals(1.5, read.absolutePosition!!.x)
        assertEquals(2.5, read.absolutePosition!!.y)
        assertEquals(3.5, read.absolutePosition!!.z)
        assertEquals(0.4f, read.lookOrientation!!.yaw)
        assertEquals(9, read.teleportAck!!.teleportId)
        assertEquals(3, read.mountedTo)
        assertEquals(0.25, read.velocity!!.x)
        assertNull(read.riderMovementStates)
    }

    @Test
    fun `world and setup configuration packets`() {
        assertEquals(4, serialize(SetUpdateRatePacket(20)).size)
        assertEquals(4, serialize(SetTimeDilationPacket(1f)).size)

        val viewRadiusBytes = serialize(ViewRadiusPacket(6))
        assertEquals(4, viewRadiusBytes.size)
        val readViewRadius = ViewRadiusPacket()
        readViewRadius.read(TestReadBuffer(viewRadiusBytes))
        assertEquals(6, readViewRadius.value)

        assertEquals(10, serialize(UpdateTimeSettingsPacket()).size)
        assertEquals(8, serialize(UpdateWeatherPacket(0, 0f)).size)
        assertEquals(13, serialize(UpdateTimePacket(null)).size)

        val tags = serialize(ServerTagsPacket(linkedMapOf("a" to 1)))
        assertEquals(0x01, tags[0].toInt() and 0xFF)
        assertEquals(1, tags[1].toInt())
    }

    @Test
    fun `asset initialize carries the size and the asset inline`() {
        val packet = AssetInitializePacket(Asset("hash", "name"), 1234)
        val bytes = serialize(packet)

        // Size (int LE) + 64-byte hash + name length (VarInt) + name bytes.
        assertEquals(73, bytes.size)
        assertEquals(1234, readIntLE(bytes, 0))
        assertEquals("hash", String(bytes, 4, 4, Charsets.US_ASCII))
    }

    @Test
    fun `the world join packets keep the layout of the protocol`() {
        assertEquals(4, serialize(SetEntitySeedPacket(42)).size)
        assertEquals(42, readIntLE(serialize(SetEntitySeedPacket(42)), 0))

        val features = serialize(UpdateFeaturesPacket(linkedMapOf(ClientFeature.MANTLING to true)))
        assertEquals(4, features.size)
        assertEquals(0x01, features[0].toInt() and 0xFF)
        assertEquals(1, features[1].toInt())
        assertEquals(ClientFeature.MANTLING.id, features[2].toInt())
        assertEquals(1, features[3].toInt())

        assertEquals(8, serialize(UpdateSunSettingsPacket(0.5f, 1.5f)).size)
        assertEquals(20, serialize(UpdatePostFxSettingsPacket()).size)
        assertEquals(13, serialize(UpdateWorldMapSettingsPacket()).size)

        // Combat config (1) + player config (1) + world config (7).
        val gameplay = serialize(WorldGameplayConfigPacket())
        assertEquals(9, gameplay.size)
        assertEquals(0x03, gameplay[0].toInt() and 0xFF)
        assertEquals(ArmorVisibilityOption.ALL.id, gameplay[1].toInt())

        val chunk = SetChunkPacket(-2, 0, 1)
        val chunkBytes = serialize(chunk)
        assertEquals(SetChunkPacket.EMPTY_SIZE, chunkBytes.size)
        assertTrue(chunk.definition.compressed)
        assertEquals(0x00, chunkBytes[0].toInt() and 0xFF)
        assertEquals(-2, readIntLE(chunkBytes, 1))
        assertEquals(0, readIntLE(chunkBytes, 5))
        assertEquals(1, readIntLE(chunkBytes, 9))
        assertEquals(-1, readIntLE(chunkBytes, 13))
        assertEquals(-1, readIntLE(chunkBytes, 17))
        assertEquals(-1, readIntLE(chunkBytes, 21))
    }

    @Test
    fun `the world gameplay config writes custom configuration`() {
        val packet = WorldGameplayConfigPacket(
            combatConfig = CombatConfig(displayHealthBars = false, displayCombatText = true),
            playerConfig = PlayerConfig(armorVisibility = ArmorVisibilityOption.NONE),
            worldConfig = WorldConfig(blockedRootInteractionTagIndexes = mutableListOf(3, 7)),
        )
        val bytes = serialize(packet)
        val reader = TestReadBuffer(bytes)

        val readCombat = CombatConfig().also { it.read(reader) }
        val readPlayer = PlayerConfig().also { it.read(reader) }
        val readWorld = WorldConfig().also { it.read(reader) }

        assertEquals(false, readCombat.displayHealthBars)
        assertEquals(true, readCombat.displayCombatText)
        assertEquals(ArmorVisibilityOption.NONE, readPlayer.armorVisibility)
        assertEquals(listOf(3, 7), readWorld.blockedRootInteractionTagIndexes)
    }
}
