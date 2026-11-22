package dev.sectile.engine.world

import dev.sectile.engine.api.player.Player
import dev.sectile.network.protocol.chat.outbound.RawMessagePacket
import dev.sectile.network.protocol.connection.outbound.ServerTagsPacket
import dev.sectile.network.protocol.entity.outbound.EntityUpdatesPacket
import dev.sectile.network.protocol.entity.structures.EntityUpdate
import dev.sectile.network.protocol.entity.structures.MovementStatesComponentUpdate
import dev.sectile.network.protocol.entity.structures.TransformComponentUpdate
import dev.sectile.network.protocol.player.enums.GameMode
import dev.sectile.network.protocol.player.outbound.JoinWorldPacket
import dev.sectile.network.protocol.player.outbound.SetClientIdPacket
import dev.sectile.network.protocol.player.outbound.SetEntitySeedPacket
import dev.sectile.network.protocol.player.outbound.SetGameModePacket
import dev.sectile.network.protocol.player.outbound.SetTimeDilationPacket
import dev.sectile.network.protocol.player.outbound.SetUpdateRatePacket
import dev.sectile.network.protocol.player.outbound.UpdateFeaturesPacket
import dev.sectile.network.protocol.player.structures.MovementStates
import dev.sectile.network.protocol.shared.InstantData
import dev.sectile.network.protocol.shared.math.Direction
import dev.sectile.network.protocol.shared.math.ModelTransform
import dev.sectile.network.protocol.shared.math.Position
import dev.sectile.network.protocol.world.both.ViewRadiusPacket
import dev.sectile.network.protocol.world.outbound.ClearChunksPacket
import dev.sectile.network.protocol.world.outbound.SetChunkPacket
import dev.sectile.network.protocol.world.outbound.SetColumnPacket
import dev.sectile.network.protocol.world.outbound.UpdatePostFxSettingsPacket
import dev.sectile.network.protocol.world.outbound.UpdateSunSettingsPacket
import dev.sectile.network.protocol.world.outbound.UpdateTimePacket
import dev.sectile.network.protocol.world.outbound.UpdateTimeSettingsPacket
import dev.sectile.network.protocol.world.outbound.UpdateWeatherPacket
import dev.sectile.network.protocol.world.outbound.UpdateWorldMapSettingsPacket
import dev.sectile.network.protocol.world.outbound.WorldGameplayConfigPacket
import java.time.Instant
import java.util.UUID

/**
 * Sends the world to a player.
 *
 * The server does not have real chunks yet, so the world is an empty void: the settings, the
 * identity of the player and the chunks around the spawn point.
 */
object WorldView {

    /** Number of updates per second the client receives. */
    private const val UPDATES_PER_SECOND = 20

    /** Seed of the entities of the world. */
    private const val ENTITY_SEED = 0

    /** Radius of the view, in blocks. */
    private const val VIEW_RADIUS_BLOCKS = 1024

    /** Radius, in chunks, of the spawn area. */
    private const val SPAWN_CHUNK_RADIUS = 2

    /** Lowest section of the world. */
    private const val MIN_SECTION_Y = 0

    /** Highest section of the world. */
    private const val MAX_SECTION_Y = 9

    /** UUID of the world. */
    private val WORLD_UUID: UUID = UUID(0L, 0L)

    /** Sends the full initial world state and the spawn chunks. */
    fun send(player: Player) {
        val connection = player.connection
        val entityId = connection.session.entityId

        connection.sendPackets(
            ClearChunksPacket(),
            JoinWorldPacket(
                clearWorld = true,
                fadeInOut = false,
                worldUuid = WORLD_UUID,
                minSectionY = MIN_SECTION_Y,
                maxSectionY = MAX_SECTION_Y,
            ),
            ViewRadiusPacket(VIEW_RADIUS_BLOCKS),
            SetEntitySeedPacket(ENTITY_SEED),
            SetClientIdPacket(entityId),
            SetTimeDilationPacket(1f),
            UpdateFeaturesPacket(LinkedHashMap()),
            UpdateSunSettingsPacket(),
            UpdatePostFxSettingsPacket(),
            WorldGameplayConfigPacket(),
            SetUpdateRatePacket(UPDATES_PER_SECOND),
            playerEntityUpdate(entityId),
            SetGameModePacket(GameMode.ADVENTURE),
            UpdateTimeSettingsPacket(),
            UpdateTimePacket(InstantData().also { it.seconds = Instant.now().epochSecond }),
            UpdateWeatherPacket(0, 0f),
            UpdateWorldMapSettingsPacket(),
            ServerTagsPacket(LinkedHashMap()),
            RawMessagePacket(rawText = "test"),
        )

        sendSpawnChunks(player)
    }

    /** Sends the empty chunks around the spawn point. */
    private fun sendSpawnChunks(player: Player) {
        val connection = player.connection
        for (cx in -SPAWN_CHUNK_RADIUS..SPAWN_CHUNK_RADIUS) {
            for (cz in -SPAWN_CHUNK_RADIUS..SPAWN_CHUNK_RADIUS) {
                connection.sendPackets(
                    SetColumnPacket(cx, cz),
                    SetChunkPacket(cx, 0, cz),
                )
            }
        }
    }

    /** Builds the update that places the player in the world. */
    private fun playerEntityUpdate(playerEntityId: Int): EntityUpdatesPacket = EntityUpdatesPacket(
        updates = mutableListOf(
            EntityUpdate(
                networkId = playerEntityId,
                updates = mutableListOf(
                    TransformComponentUpdate(
                        ModelTransform(
                            position = Position(0.0, 0.0, 0.0),
                            bodyOrientation = Direction(0f, 0f, 0f),
                            lookOrientation = Direction(0f, 0f, 0f),
                        ),
                    ),
                    MovementStatesComponentUpdate(MovementStates(idle = true, onGround = true)),
                ),
            ),
        ),
    )
}
