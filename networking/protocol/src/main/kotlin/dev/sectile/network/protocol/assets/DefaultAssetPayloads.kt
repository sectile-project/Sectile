package dev.sectile.network.protocol.assets

import dev.sectile.network.api.buffer.WriteBuffer
import dev.sectile.network.protocol.ProtocolCodec
import dev.sectile.network.protocol.VarInt
import dev.sectile.network.protocol.structures.Asset

/**
 * Minimum payloads of the asset types that the client indexes when preparing the world.
 *
 * Most of the 47 `Update*` packets can be empty in a void world, but the client assumes that the
 * base entries registered by the official server exist before loading packs:
 *  - `BlockHitboxes` (id 41): hitbox `0` (`0..1` on all three axes).
 *  - `BlockTypes` (id 40): blocks `0` (`Empty`) and `1` (`Unknown`).
 *  - `Fluids` (id 83): fluids `0` (`Empty`) and `1` (`Unknown`).
 *  - `ItemCategories` (id 56): category `Default` with a subcategory `Default`.
 *  - `Weathers` (id 47): weather `0` (`Unknown`).
 *  - `Environments` (id 61): environment `0` (`Unknown`).
 *  - `AudioCategories` (id 80): root categories `0..5` (`EMPTY` and the 5 audio roots).
 */
object DefaultAssetPayloads {

    private const val EMPTY_NAME = "Empty"
    private const val UNKNOWN_NAME = "Unknown"
    private const val DEFAULT_NAME = "Default"

    /**
     * Preloaded audio categories (`AudioCategory.PRELOAD_ORDER`, indices `0..5`).
     */
    private val PRELOADED_AUDIO_CATEGORIES: List<String> = listOf(
        "EMPTY",
        "AudioCat_Music",
        "AudioCat_Ambient",
        "AudioCat_SFX",
        "AudioCat_Voice",
        "AudioCat_UI",
    )

    /**
     * References to the common assets bundled in `Assets.zip` (`CommonAssetsIndex.hashes`)
     * that the client looks up by name when building the block/item atlases and initializing
     * the HUD, the world map, and the session audio.
     *
     * Since all these hashes are already in the client's local index, `Handle RequiredAssets`
     * resolves them locally without requesting downloads from the server.
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

    /** `UpdateBlockHitboxes` (id 41): a unit box at index 0. */
    fun writeBlockHitboxes(buf: WriteBuffer) {
        buf.writeByte(0x01) // HasBlockBaseHitboxes
        buf.writeByte(AssetUpdatePacket.UPDATE_TYPE_INIT)
        buf.writeInt(1) // maxId
        VarInt.write(buf, 1) // 1 entry in the map
        buf.writeInt(0) // key = 0
        VarInt.write(buf, 1) // 1 hitbox in the array
        buf.writeFloat(0f)
        buf.writeFloat(0f)
        buf.writeFloat(0f)
        buf.writeFloat(1f)
        buf.writeFloat(1f)
        buf.writeFloat(1f)
    }

    /** `UpdateBlockTypes` (id 40): blocks `0` (`Empty`) and `1` (`Unknown`). */
    fun writeBlockTypes(buf: WriteBuffer) {
        buf.writeByte(0x01) // HasBlockTypes
        buf.writeByte(AssetUpdatePacket.UPDATE_TYPE_INIT)
        buf.writeInt(2) // maxId
        buf.writeByte(0x00) // no additional rebuild flags
        VarInt.write(buf, 2) // 2 entries

        // 0 -> Empty (drawType = Empty(0), material = Empty(0), opacity = Transparent(3))
        buf.writeInt(0)
        writeMinimalBlockType(
            buf = buf,
            name = EMPTY_NAME,
            unknown = false,
            drawType = 0,
            material = 0,
            opacity = 3,
        )

        // 1 -> Unknown (drawType = Cube(2), material = Solid(1), opacity = Solid(0))
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
        // nullBits (4 bytes): bit 3 = HasTint, bit 4 = HasBiomeTint, bit 7 = HasName.
        buf.writeByte(0x98)
        buf.writeByte(0x00)
        buf.writeByte(0x00)
        buf.writeByte(0x00)

        buf.writeBoolean(unknown)
        buf.writeByte(drawType)
        buf.writeByte(material)
        buf.writeByte(opacity)
        buf.writeInt(0) // hitbox
        buf.writeInt(Int.MIN_VALUE) // interactionHitbox
        buf.writeFloat(1f) // modelScale
        buf.writeFloat(1f) // modelAnimationSpeed
        buf.writeBoolean(false) // looping
        buf.writeInt(0) // maxSupportDistance
        buf.writeByte(1) // blockSupportsRequiredFor = Any
        buf.writeBoolean(false) // requiresAlphaBlending
        buf.writeByte(0) // cubeShadingMode = Standard
        buf.writeByte(0) // randomRotation = None
        buf.writeByte(0) // variantRotation = None
        buf.writeByte(0) // rotationYawPlacementOffset = None
        buf.writeInt(0) // blockSoundSetIndex
        buf.writeInt(0) // physicalMaterialIndex
        buf.writeFloat(1f) // soundOcclusionOpacity
        buf.writeInt(0) // ambientSoundEventIndex

        // particleColor (3) + textureComputedColor (3) + light (4) = 10 bytes.
        buf.skip(10)

        // tint (6 faces x 4 bytes = 24 bytes): -1 (0xFFFFFFFF) by default on all faces.
        repeat(6) {
            buf.writeInt(-1)
        }

        // biomeTint (6 faces x 4 bytes = 24 bytes): 0 by default on all faces.
        buf.skip(24)

        buf.writeInt(0) // group

        // BlockMovementSettings (43 bytes)
        buf.writeBoolean(false) // isClimbable
        buf.writeFloat(1f) // climbUpSpeedMultiplier
        buf.writeFloat(1f) // climbDownSpeedMultiplier
        buf.writeFloat(1f) // climbLateralSpeedMultiplier
        buf.writeBoolean(false) // isBouncy
        buf.writeFloat(0f) // bounceVelocity
        buf.writeFloat(0.82f) // drag
        buf.writeFloat(0.18f) // friction
        buf.writeFloat(1f) // terminalVelocityModifier
        buf.writeFloat(1f) // horizontalSpeedMultiplier
        buf.writeFloat(0f) // acceleration
        buf.writeFloat(1f) // jumpForceMultiplier
        buf.writeBoolean(false) // disableAutoStep

        // placementSettings (16 bytes)
        buf.skip(16)
        buf.writeBoolean(false) // ignoreSupportWhenPlaced
        buf.writeInt(Int.MIN_VALUE) // transitionToTag

        // 25 offset slots: only slot 1 (`Name`) is present at offset 0.
        buf.writeInt(-1) // slot 0: Item
        buf.writeInt(0) // slot 1: Name
        for (slot in 2 until 25) {
            buf.writeInt(-1)
        }

        ProtocolCodec.writeVarString(buf, name, ProtocolCodec.MAX_STRING_LENGTH)
    }

    /** `UpdateFluids` (id 83): fluids `0` (`Empty`) and `1` (`Unknown`). */
    fun writeFluids(buf: WriteBuffer) {
        buf.writeByte(0x01) // HasFluids
        buf.writeByte(AssetUpdatePacket.UPDATE_TYPE_INIT)
        buf.writeInt(2) // maxId
        VarInt.write(buf, 2) // 2 entries

        buf.writeInt(0)
        writeMinimalFluid(buf, EMPTY_NAME)

        buf.writeInt(1)
        writeMinimalFluid(buf, UNKNOWN_NAME)
    }

    private fun writeMinimalFluid(buf: WriteBuffer, id: String) {
        buf.writeByte(0x04) // HasId
        buf.writeInt(8) // maxFluidLevel
        buf.writeBoolean(true) // requiresAlphaBlending
        buf.writeByte(0) // opacity = Solid
        buf.skip(4) // light
        buf.writeByte(0) // drawType = None
        buf.writeInt(0) // fluidFXIndex
        buf.writeInt(0) // blockSoundSetIndex
        buf.skip(3) // particleColor

        // 6 offset slots: only slot 0 (`Id`) is present at offset 0.
        buf.writeInt(0)
        for (slot in 1 until 6) {
            buf.writeInt(-1)
        }

        ProtocolCodec.writeVarString(buf, id, ProtocolCodec.MAX_STRING_LENGTH)
    }

    /** `UpdateItemCategories` (id 56): category `Default` with a child `Default`. */
    fun writeItemCategories(buf: WriteBuffer) {
        buf.writeByte(0x01) // HasItemCategories
        buf.writeByte(AssetUpdatePacket.UPDATE_TYPE_INIT)
        VarInt.write(buf, 1) // 1 root category
        writeItemCategory(buf, hasChild = true)
    }

    private fun writeItemCategory(buf: WriteBuffer, hasChild: Boolean) {
        // Bit 0: Id, Bit 1: Name, Bit 2: Icon, Bit 3: Children.
        buf.writeByte(0x0F)
        buf.writeInt(0) // order
        buf.writeByte(0) // infoDisplayMode = Tooltip

        // 5 offset slots: Id ("Default", 8 bytes), Name ("Default", 8 bytes), Icon ("", 1 byte), Children (offset 17), SubCategories (-1).
        buf.writeInt(0)
        buf.writeInt(8)
        buf.writeInt(16)
        buf.writeInt(17)
        buf.writeInt(-1)

        ProtocolCodec.writeVarString(buf, DEFAULT_NAME, ProtocolCodec.MAX_STRING_LENGTH)
        ProtocolCodec.writeVarString(buf, DEFAULT_NAME, ProtocolCodec.MAX_STRING_LENGTH)
        ProtocolCodec.writeVarString(buf, "", ProtocolCodec.MAX_STRING_LENGTH)
        if (hasChild) {
            VarInt.write(buf, 1)
            writeItemCategory(buf, hasChild = false)
        } else {
            VarInt.write(buf, 0)
        }
    }

    /** `UpdateWeathers` (id 47): weather `0` (`Unknown`). */
    fun writeWeathers(buf: WriteBuffer) {
        buf.writeByte(0x01) // HasWeathers
        buf.writeByte(AssetUpdatePacket.UPDATE_TYPE_INIT)
        buf.writeInt(1) // maxId
        VarInt.write(buf, 1) // 1 entry
        buf.writeInt(0) // key = 0

        // Weather: FixedBlockSize = 30, VariableFieldCount = 24 (96 bytes of slots).
        buf.writeByte(0x05) // HasFog + HasId
        buf.writeByte(0x00)
        buf.writeByte(0x00)
        buf.writeByte(0x00)
        buf.writeFloat(-96f) // fogNear
        buf.writeFloat(1024f) // fogFar
        buf.skip(18) // waterTint (3) + fogOptions (15)

        // 24 offset slots: only slot 0 (`Id`) is present at offset 0.
        buf.writeInt(0)
        for (slot in 1 until 24) {
            buf.writeInt(-1)
        }

        ProtocolCodec.writeVarString(buf, UNKNOWN_NAME, ProtocolCodec.MAX_STRING_LENGTH)
    }

    /** `UpdateEnvironments` (id 61): environment `0` (`Unknown`). */
    fun writeEnvironments(buf: WriteBuffer) {
        buf.writeByte(0x01) // HasEnvironments
        buf.writeByte(AssetUpdatePacket.UPDATE_TYPE_INIT)
        buf.writeInt(1) // maxId
        buf.writeBoolean(true) // rebuildMapGeometry
        VarInt.write(buf, 1) // 1 entry
        buf.writeInt(0) // key = 0

        // WorldEnvironment: FixedBlockSize = 4, VariableFieldCount = 3 (12 bytes of slots).
        buf.writeByte(0x03) // HasWaterTint + HasId
        buf.writeByte(0x0A) // waterTint.red = 10
        buf.writeByte(0x33) // waterTint.green = 51
        buf.writeByte(0x55) // waterTint.blue = 85

        buf.writeInt(0) // slot 0: Id
        buf.writeInt(-1) // slot 1: FluidParticles
        buf.writeInt(-1) // slot 2: TagIndexes

        ProtocolCodec.writeVarString(buf, UNKNOWN_NAME, ProtocolCodec.MAX_STRING_LENGTH)
    }

    /**
     * `UpdateAudioCategories` (id 80): the 6 preloaded root categories (`0..5`, `EMPTY` and
     * the 5 audio roots that the client expects at session startup).
     */
    fun writeAudioCategories(buf: WriteBuffer) {
        buf.writeByte(0x01) // HasCategories
        buf.writeByte(AssetUpdatePacket.UPDATE_TYPE_INIT)
        buf.writeInt(PRELOADED_AUDIO_CATEGORIES.size) // maxId = 6
        VarInt.write(buf, PRELOADED_AUDIO_CATEGORIES.size) // 6 entries

        PRELOADED_AUDIO_CATEGORIES.forEachIndexed { index, categoryId ->
            buf.writeInt(index) // key
            // AudioCategory: FixedBlockSize = 13, VariableFieldCount = 3 (12 bytes of slots).
            buf.writeByte(0x01) // HasId
            buf.writeFloat(1f) // volume
            buf.writeInt(-1) // parentAudioCategoryIndex
            buf.writeFloat(-100f) // maxDuckingDb

            buf.writeInt(0) // slot 0: Id
            buf.writeInt(-1) // slot 1: StateBindings
            buf.writeInt(-1) // slot 2: DuckingRules

            ProtocolCodec.writeVarString(buf, categoryId, ProtocolCodec.MAX_STRING_LENGTH)
        }
    }
}
