package dev.sectile.network.protocol.asset

import dev.sectile.network.protocol.ProtocolPacketTable
import dev.sectile.network.protocol.asset.outbound.AssetUpdatePacket

/**
 * All 47 asset update packets expected by the Hytale client before `WorldLoadFinished`,
 * ordered to match the client's registration sequence.
 */
object AssetTypes {

    val ALL: List<AssetUpdatePacket> = listOf(
        AssetUpdatePacket(ProtocolPacketTable.UPDATE_AMBIENCE_FX, AssetUpdatePacket.Shape.LIST_WITH_MAX_ID),
        AssetUpdatePacket(
            ProtocolPacketTable.UPDATE_BLOCK_HITBOXES,
            AssetUpdatePacket.Shape.LIST_WITH_MAX_ID,
            36,
            DefaultAssetPayloads::writeBlockHitboxes,
        ),
        AssetUpdatePacket(
            ProtocolPacketTable.UPDATE_BLOCK_TYPES,
            AssetUpdatePacket.Shape.LIST_WITH_MAX_ID_AND_FLAGS,
            584,
            DefaultAssetPayloads::writeBlockTypes,
        ),
        AssetUpdatePacket(
            ProtocolPacketTable.UPDATE_ENVIRONMENTS,
            AssetUpdatePacket.Shape.LIST_WITH_MAX_ID_AND_FLAGS,
            36,
            DefaultAssetPayloads::writeEnvironments,
        ),
        AssetUpdatePacket(ProtocolPacketTable.UPDATE_FLUID_FX, AssetUpdatePacket.Shape.LIST_WITH_MAX_ID),
        AssetUpdatePacket(ProtocolPacketTable.UPDATE_ITEMS, AssetUpdatePacket.Shape.LIST_WITH_FLAGS_AND_SLOTS),
        AssetUpdatePacket(
            ProtocolPacketTable.UPDATE_ITEM_CATEGORIES,
            AssetUpdatePacket.Shape.LIST,
            91,
            DefaultAssetPayloads::writeItemCategories,
        ),
        AssetUpdatePacket(ProtocolPacketTable.UPDATE_PARTICLE_SPAWNERS, AssetUpdatePacket.Shape.LIST_WITH_SLOTS),
        AssetUpdatePacket(ProtocolPacketTable.UPDATE_PARTICLE_SYSTEMS, AssetUpdatePacket.Shape.LIST_WITH_SLOTS),
        AssetUpdatePacket(ProtocolPacketTable.UPDATE_RESOURCE_TYPES, AssetUpdatePacket.Shape.LIST),
        AssetUpdatePacket(
            ProtocolPacketTable.UPDATE_WEATHERS,
            AssetUpdatePacket.Shape.LIST_WITH_MAX_ID,
            145,
            DefaultAssetPayloads::writeWeathers,
        ),
        AssetUpdatePacket(ProtocolPacketTable.UPDATE_TRANSLATIONS, AssetUpdatePacket.Shape.LIST),
        AssetUpdatePacket(ProtocolPacketTable.UPDATE_FIELDCRAFT_CATEGORIES, AssetUpdatePacket.Shape.LIST),
        AssetUpdatePacket(ProtocolPacketTable.UPDATE_TRAILS, AssetUpdatePacket.Shape.LIST),
        AssetUpdatePacket(ProtocolPacketTable.UPDATE_ENTITY_EFFECTS, AssetUpdatePacket.Shape.LIST_WITH_MAX_ID),
        AssetUpdatePacket(ProtocolPacketTable.UPDATE_BLOCK_PARTICLE_SETS, AssetUpdatePacket.Shape.LIST),
        AssetUpdatePacket(ProtocolPacketTable.UPDATE_ITEM_PLAYER_ANIMATIONS, AssetUpdatePacket.Shape.LIST),
        AssetUpdatePacket(ProtocolPacketTable.UPDATE_SOUND_EVENTS, AssetUpdatePacket.Shape.LIST_WITH_MAX_ID),
        AssetUpdatePacket(ProtocolPacketTable.UPDATE_INTERACTIONS, AssetUpdatePacket.Shape.LIST_WITH_MAX_ID),
        AssetUpdatePacket(ProtocolPacketTable.UPDATE_ROOT_INTERACTIONS, AssetUpdatePacket.Shape.LIST_WITH_MAX_ID),
        AssetUpdatePacket(ProtocolPacketTable.UPDATE_UNARMED_INTERACTIONS, AssetUpdatePacket.Shape.LIST),
        AssetUpdatePacket(ProtocolPacketTable.UPDATE_BLOCK_SOUND_SETS, AssetUpdatePacket.Shape.LIST_WITH_MAX_ID),
        AssetUpdatePacket(ProtocolPacketTable.UPDATE_ITEM_QUALITIES, AssetUpdatePacket.Shape.LIST_WITH_MAX_ID),
        AssetUpdatePacket(ProtocolPacketTable.UPDATE_ITEM_RETICLES, AssetUpdatePacket.Shape.LIST_WITH_MAX_ID),
        AssetUpdatePacket(ProtocolPacketTable.UPDATE_HITBOX_COLLISION_CONFIG, AssetUpdatePacket.Shape.LIST_WITH_MAX_ID),
        AssetUpdatePacket(ProtocolPacketTable.UPDATE_REPULSION_CONFIG, AssetUpdatePacket.Shape.LIST_WITH_MAX_ID),
        AssetUpdatePacket(ProtocolPacketTable.UPDATE_MODELVFXS, AssetUpdatePacket.Shape.LIST_WITH_MAX_ID),
        AssetUpdatePacket(ProtocolPacketTable.UPDATE_ENTITY_UI_COMPONENTS, AssetUpdatePacket.Shape.LIST_WITH_MAX_ID),
        AssetUpdatePacket(ProtocolPacketTable.UPDATE_ENTITY_STAT_TYPES, AssetUpdatePacket.Shape.LIST_WITH_MAX_ID),
        AssetUpdatePacket(ProtocolPacketTable.UPDATE_SOUND_SETS, AssetUpdatePacket.Shape.LIST_WITH_MAX_ID),
        AssetUpdatePacket(ProtocolPacketTable.UPDATE_BLOCK_BREAKING_DECALS, AssetUpdatePacket.Shape.LIST_NO_NULLS),
        AssetUpdatePacket(ProtocolPacketTable.UPDATE_BLOCK_SETS, AssetUpdatePacket.Shape.LIST),
        AssetUpdatePacket(
            ProtocolPacketTable.UPDATE_AUDIO_CATEGORIES,
            AssetUpdatePacket.Shape.LIST_WITH_MAX_ID,
            254,
            DefaultAssetPayloads::writeAudioCategories,
        ),
        AssetUpdatePacket(
            ProtocolPacketTable.UPDATE_FLUIDS,
            AssetUpdatePacket.Shape.LIST_WITH_MAX_ID,
            123,
            DefaultAssetPayloads::writeFluids,
        ),
        AssetUpdatePacket(ProtocolPacketTable.UPDATE_RECIPES, AssetUpdatePacket.Shape.LIST_WITH_SLOTS),
        AssetUpdatePacket(ProtocolPacketTable.UPDATE_TAG_PATTERNS, AssetUpdatePacket.Shape.LIST_WITH_MAX_ID),
        AssetUpdatePacket(ProtocolPacketTable.UPDATE_ITEM_SOUND_SETS, AssetUpdatePacket.Shape.LIST_WITH_MAX_ID),
        AssetUpdatePacket(ProtocolPacketTable.UPDATE_REVERB_EFFECTS, AssetUpdatePacket.Shape.LIST_WITH_MAX_ID),
        AssetUpdatePacket(ProtocolPacketTable.UPDATE_EQUALIZER_EFFECTS, AssetUpdatePacket.Shape.LIST_WITH_MAX_ID),
        AssetUpdatePacket(ProtocolPacketTable.UPDATE_PHYSICAL_MATERIALS, AssetUpdatePacket.Shape.LIST_WITH_MAX_ID),
        AssetUpdatePacket(ProtocolPacketTable.UPDATE_MUSIC_CONTAINERS, AssetUpdatePacket.Shape.LIST_WITH_MAX_ID),
        AssetUpdatePacket(ProtocolPacketTable.UPDATE_AUDIO_STATES, AssetUpdatePacket.Shape.LIST_WITH_MAX_ID),
        AssetUpdatePacket(ProtocolPacketTable.UPDATE_CONNECTED_BLOCK_RULE_SETS, AssetUpdatePacket.Shape.LIST),
        AssetUpdatePacket(ProtocolPacketTable.UPDATE_MUSIC_TRANSITIONS, AssetUpdatePacket.Shape.LIST_WITH_MAX_ID),
        AssetUpdatePacket(ProtocolPacketTable.UPDATE_STINGERS, AssetUpdatePacket.Shape.LIST_WITH_MAX_ID),
        AssetUpdatePacket(ProtocolPacketTable.UPDATE_BEAM_CONFIGS, AssetUpdatePacket.Shape.LIST_WITH_MAX_ID),
        AssetUpdatePacket(ProtocolPacketTable.UPDATE_ABILITY_KEYWORDS, AssetUpdatePacket.Shape.LIST_WITH_MAX_ID),
    )
}
