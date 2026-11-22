package dev.sectile.network.protocol.asset

import dev.sectile.network.api.buffer.WriteBuffer
import dev.sectile.network.protocol.DataSize
import dev.sectile.network.protocol.asset.outbound.AssetUpdatePacket
import dev.sectile.network.protocol.asset.structures.Asset

/**
 * Minimum payloads for asset registries where the client indexes default entries
 * (`Empty`, `Unknown`, `Default`, and preloaded audio roots) before loading packs.
 */
object DefaultAssetPayloads {

    private const val EMPTY_NAME = "Empty"
    private const val UNKNOWN_NAME = "Unknown"
    private const val DEFAULT_NAME = "Default"

    private val PRELOADED_AUDIO_CATEGORIES: List<String> = listOf(
        "EMPTY",
        "AudioCat_Music",
        "AudioCat_Ambient",
        "AudioCat_SFX",
        "AudioCat_Voice",
        "AudioCat_UI",
    )

    /**
     * Common assets bundled in `Assets.zip` that the client resolves locally by hash
     * when building atlases, HUD, world map, and session audio.
     */
    val REQUIRED_COMMON_ASSETS: List<Asset> = listOf(
        Asset(
            "11c5f4d7cfa18a12e83caa3c01b64a2770bb5b45fa7fd448c1a51cebe52e3ea4",
            "BlockTextures/Unknown.png",
        ),
        Asset(
            "11c5f4d7cfa18a12e83caa3c01b64a2770bb5b45fa7fd448c1a51cebe52e3ea4",
            "Items/Unknown.png",
        ),
        Asset(
            "e585f1a8601b73d8b9ceb0c0b10beae3c1e71e034d76b4c6255318250e2dccc0",
            "UI/WorldMap/MapMarkers/Player.png",
        ),
        Asset(
            "cbb1d390c4478f4bebe5af743aefce38666ddfe41ded0830e079b6f5bdb8a6e9",
            "UI/WorldMap/MapMarkers/PlayerAbove.png",
        ),
        Asset(
            "1ab6f588811e1cbc626bfc0a46e41d2a0e3ad10a0782320afeb73dd15788ef68",
            "UI/WorldMap/MapMarkers/PlayerBelow.png",
        ),
        Asset(
            "03ed6c38b7e398dd0ee488b5ad5ae803b4ecb53778ca4e35fd4ea2395cbab382",
            "UI/WorldMap/MapMarkers/Coordinate.png",
        ),
        Asset(
            "f4aefcc4a7ddcbcbd58ba27c385a606f5b186d7c5e198faf6916d6ad874b954c",
            "UI/WorldMap/MapMarkers/OutsideViewMarker.png",
        ),
        Asset(
            "20bf34cb90490a10407cb784ab5c73083ae267b3ee38b473bd754c9b66e66fc6",
            "UI/DamageIndicators/HitIndicatorBasic.png",
        ),
        Asset(
            "48eb0128c823c1ec538dc9ff46e15e846f00d8bedc4ca42818cf587e66d75788",
            "UI/DamageIndicators/HitIndicatorCritic.png",
        ),
        Asset(
            "8a0290bc07a490222ba446f2f6186c1370bdebce53df0f85a63e4c92c6bafa90",
            "UI/DamageIndicators/HitIndicatorMelee.png",
        ),
        Asset(
            "f16d76533e1ac716285b5a55d26c1bf7f7ab9b7569de9dd078f9ba174c7f4900",
            "UI/DamageIndicators/HitIndicatorBlocked.png",
        ),
        Asset(
            "740aa2ff7ffd4405cbe78bf56c1e2d977115d9b63839a0265adbadd0695d7270",
            "UI/DamageScreenEffect.png",
        ),
        Asset(
            "1fccb9ad89e227b792969636ce1fd5e94d9bdb6e1bfb592674c6145d8c46d30c",
            "UI/HealthAlertScreenEffect.png",
        ),
        Asset(
            "cde8cfb40c82bd64ca49589073e98f46b38862bc4594b204d7829a97e4c5d700",
            "Sounds/PlayerActions/Hurt/Heartbeat_90bpm.ogg",
        ),
        Asset(
            "d64fd1bbb7b7d6c044425337664edd00bb8d0bf71b61a88e5726af6e34cc1b86",
            "Sounds/PlayerActions/Hurt/Heartbeat_120bpm.ogg",
        ),
        Asset(
            "d1d2e975c52a609fcfc043df496360f96a3132d978da70f56b7cd84717aa4004",
            "Sounds/PlayerActions/Hurt/Heartbeat_150bpm.ogg",
        ),
        Asset(
            "f360cd5e0b8f0b6e77400da2c8594eaf3f17dd4512d9e6175d243122050e3593",
            "Sounds/PlayerActions/Hurt/Heartbeat_Background.ogg",
        ),
        Asset(
            "e70bfa1086a8a85a4ac979ce0e95eff2a53ad0a7461b2439d261f7449889d58f",
            "ScreenEffects/VoidEscalationScreenEffect.png",
        ),
    )

    fun writeBlockHitboxes(buf: WriteBuffer) {
        buf.writeByte(0x01)
        buf.writeByte(AssetUpdatePacket.UPDATE_TYPE_INIT)
        buf.writeInt(1)
        buf.writeVarInt(1)
        buf.writeInt(0)
        buf.writeVarInt(1)
        buf.writeFloat(0f)
        buf.writeFloat(0f)
        buf.writeFloat(0f)
        buf.writeFloat(1f)
        buf.writeFloat(1f)
        buf.writeFloat(1f)
    }

    fun writeBlockTypes(buf: WriteBuffer) {
        buf.writeByte(0x01)
        buf.writeByte(AssetUpdatePacket.UPDATE_TYPE_INIT)
        buf.writeInt(2)
        buf.writeByte(0x00)
        buf.writeVarInt(2)

        buf.writeInt(0)
        writeMinimalBlockType(
            buf = buf,
            name = EMPTY_NAME,
            unknown = false,
            drawType = 0,
            material = 0,
            opacity = 3,
        )

        buf.writeInt(1)
        writeMinimalBlockType(
            buf = buf,
            name = UNKNOWN_NAME,
            unknown = true,
            drawType = 2,
            material = 1,
            opacity = 0,
        )
    }

    private fun writeMinimalBlockType(
        buf: WriteBuffer,
        name: String,
        unknown: Boolean,
        drawType: Int,
        material: Int,
        opacity: Int,
    ) {
        // nullBits: bit 3 = HasTint, bit 4 = HasBiomeTint, bit 7 = HasName.
        buf.writeByte(0x98)
        buf.writeByte(0x00)
        buf.writeByte(0x00)
        buf.writeByte(0x00)

        buf.writeBoolean(unknown)
        buf.writeByte(drawType)
        buf.writeByte(material)
        buf.writeByte(opacity)
        buf.writeInt(0)
        buf.writeInt(Int.MIN_VALUE)
        buf.writeFloat(1f)
        buf.writeFloat(1f)
        buf.writeBoolean(false)
        buf.writeInt(0)
        buf.writeByte(1)
        buf.writeBoolean(false)
        buf.writeByte(0)
        buf.writeByte(0)
        buf.writeByte(0)
        buf.writeByte(0)
        buf.writeInt(0)
        buf.writeInt(0)
        buf.writeFloat(1f)
        buf.writeInt(0)

        // particleColor (3) + textureComputedColor (3) + light (4)
        buf.skip(10)

        // Default tint (-1) on all 6 faces
        repeat(6) {
            buf.writeInt(-1)
        }

        // Default biomeTint (0) on all 6 faces
        buf.skip(24)

        buf.writeInt(0)

        buf.writeBoolean(false)
        buf.writeFloat(1f)
        buf.writeFloat(1f)
        buf.writeFloat(1f)
        buf.writeBoolean(false)
        buf.writeFloat(0f)
        buf.writeFloat(0.82f)
        buf.writeFloat(0.18f)
        buf.writeFloat(1f)
        buf.writeFloat(1f)
        buf.writeFloat(0f)
        buf.writeFloat(1f)
        buf.writeBoolean(false)

        // placementSettings
        buf.skip(16)
        buf.writeBoolean(false)
        buf.writeInt(Int.MIN_VALUE)

        // 25 offset slots: only slot 1 (Name) is present at offset 0.
        buf.writeInt(-1)
        buf.writeInt(0)
        for (slot in 2 until 25) {
            buf.writeInt(-1)
        }

        buf.writeVarString(name, DataSize.MAX_STRING_LENGTH)
    }

    fun writeFluids(buf: WriteBuffer) {
        buf.writeByte(0x01)
        buf.writeByte(AssetUpdatePacket.UPDATE_TYPE_INIT)
        buf.writeInt(2)
        buf.writeVarInt(2)

        buf.writeInt(0)
        writeMinimalFluid(buf, EMPTY_NAME)

        buf.writeInt(1)
        writeMinimalFluid(buf, UNKNOWN_NAME)
    }

    private fun writeMinimalFluid(buf: WriteBuffer, id: String) {
        buf.writeByte(0x04)
        buf.writeInt(8)
        buf.writeBoolean(true)
        buf.writeByte(0)
        buf.skip(4)
        buf.writeByte(0)
        buf.writeInt(0)
        buf.writeInt(0)
        buf.skip(3)

        // 6 offset slots: only slot 0 (Id) is present at offset 0.
        buf.writeInt(0)
        for (slot in 1 until 6) {
            buf.writeInt(-1)
        }

        buf.writeVarString(id, DataSize.MAX_STRING_LENGTH)
    }

    fun writeItemCategories(buf: WriteBuffer) {
        buf.writeByte(0x01)
        buf.writeByte(AssetUpdatePacket.UPDATE_TYPE_INIT)
        buf.writeVarInt(1)
        writeItemCategory(buf, hasChild = true)
    }

    private fun writeItemCategory(buf: WriteBuffer, hasChild: Boolean) {
        buf.writeByte(0x0F)
        buf.writeInt(0)
        buf.writeByte(0)

        // Offset slots: Id ("Default", 8), Name ("Default", 8), Icon ("", 1), Children (17), SubCategories (-1).
        buf.writeInt(0)
        buf.writeInt(8)
        buf.writeInt(16)
        buf.writeInt(17)
        buf.writeInt(-1)

        buf.writeVarString(DEFAULT_NAME, DataSize.MAX_STRING_LENGTH)
        buf.writeVarString(DEFAULT_NAME, DataSize.MAX_STRING_LENGTH)
        buf.writeVarString("", DataSize.MAX_STRING_LENGTH)
        if (hasChild) {
            buf.writeVarInt(1)
            writeItemCategory(buf, hasChild = false)
        } else {
            buf.writeVarInt(0)
        }
    }

    fun writeWeathers(buf: WriteBuffer) {
        buf.writeByte(0x01)
        buf.writeByte(AssetUpdatePacket.UPDATE_TYPE_INIT)
        buf.writeInt(1)
        buf.writeVarInt(1)
        buf.writeInt(0)

        buf.writeByte(0x05)
        buf.writeByte(0x00)
        buf.writeByte(0x00)
        buf.writeByte(0x00)
        buf.writeFloat(-96f)
        buf.writeFloat(1024f)
        buf.skip(18)

        // 24 offset slots: only slot 0 (Id) is present at offset 0.
        buf.writeInt(0)
        for (slot in 1 until 24) {
            buf.writeInt(-1)
        }

        buf.writeVarString(UNKNOWN_NAME, DataSize.MAX_STRING_LENGTH)
    }

    fun writeEnvironments(buf: WriteBuffer) {
        buf.writeByte(0x01)
        buf.writeByte(AssetUpdatePacket.UPDATE_TYPE_INIT)
        buf.writeInt(1)
        buf.writeBoolean(true)
        buf.writeVarInt(1)
        buf.writeInt(0)

        buf.writeByte(0x03)
        buf.writeByte(0x0A)
        buf.writeByte(0x33)
        buf.writeByte(0x55)

        buf.writeInt(0)
        buf.writeInt(-1)
        buf.writeInt(-1)

        buf.writeVarString(UNKNOWN_NAME, DataSize.MAX_STRING_LENGTH)
    }

    fun writeAudioCategories(buf: WriteBuffer) {
        buf.writeByte(0x01)
        buf.writeByte(AssetUpdatePacket.UPDATE_TYPE_INIT)
        buf.writeInt(PRELOADED_AUDIO_CATEGORIES.size)
        buf.writeVarInt(PRELOADED_AUDIO_CATEGORIES.size)

        PRELOADED_AUDIO_CATEGORIES.forEachIndexed { index, categoryId ->
            buf.writeInt(index)
            buf.writeByte(0x01)
            buf.writeFloat(1f)
            buf.writeInt(-1)
            buf.writeFloat(-100f)

            buf.writeInt(0)
            buf.writeInt(-1)
            buf.writeInt(-1)

            buf.writeVarString(categoryId, DataSize.MAX_STRING_LENGTH)
        }
    }
}
