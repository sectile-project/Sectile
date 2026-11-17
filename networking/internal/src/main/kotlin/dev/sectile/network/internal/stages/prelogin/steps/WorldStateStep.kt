package dev.sectile.network.internal.stages.prelogin.steps

import dev.sectile.network.internal.stages.SpawnChunks
import dev.sectile.network.internal.stages.StageContext
import dev.sectile.network.protocol.entities.EntityUpdate
import dev.sectile.network.protocol.entities.EntityUpdatesPacket
import dev.sectile.network.protocol.entities.MovementStatesComponentUpdate
import dev.sectile.network.protocol.entities.TransformComponentUpdate
import dev.sectile.network.protocol.enums.GameMode
import dev.sectile.network.protocol.packet.server.RawMessagePacket
import dev.sectile.network.protocol.player.JoinWorldPacket
import dev.sectile.network.protocol.player.SetClientIdPacket
import dev.sectile.network.protocol.player.SetEntitySeedPacket
import dev.sectile.network.protocol.player.SetGameModePacket
import dev.sectile.network.protocol.setup.ServerTagsPacket
import dev.sectile.network.protocol.setup.SetTimeDilationPacket
import dev.sectile.network.protocol.setup.SetUpdateRatePacket
import dev.sectile.network.protocol.setup.UpdateFeaturesPacket
import dev.sectile.network.protocol.setup.ViewRadiusPacket
import dev.sectile.network.protocol.structures.Direction
import dev.sectile.network.protocol.structures.InstantData
import dev.sectile.network.protocol.structures.ModelTransform
import dev.sectile.network.protocol.structures.MovementStates
import dev.sectile.network.protocol.structures.Position
import dev.sectile.network.protocol.world.ClearChunksPacket
import dev.sectile.network.protocol.world.UpdatePostFxSettingsPacket
import dev.sectile.network.protocol.world.UpdateSunSettingsPacket
import dev.sectile.network.protocol.world.UpdateTimePacket
import dev.sectile.network.protocol.world.UpdateTimeSettingsPacket
import dev.sectile.network.protocol.world.UpdateWeatherPacket
import dev.sectile.network.protocol.world.UpdateWorldMapSettingsPacket
import dev.sectile.network.protocol.world.WorldGameplayConfigPacket
import java.time.Instant
import java.util.UUID

/**
 * Sends the state of the world and of the player.
 *
 * The server does not have a world yet, so the client receives an empty one: the chunks around
 * the spawn point, the identity of the player and the settings of the void.
 */
internal object WorldStateStep {

    /** Number of updates per second the client receives. */
    private const val UPDATES_PER_SECOND = 20

    /** Seed of the entities of the world. */
    private const val ENTITY_SEED = 0

    /** Radius of the view, in blocks. */
    private const val VIEW_RADIUS_BLOCKS = 1024

    /** Lowest section of the world. */
    private const val MIN_SECTION_Y = 0

    /** Highest section of the world. */
    private const val MAX_SECTION_Y = 9

    /** UUID of the world. */
    private val WORLD_UUID: UUID = UUID(0L, 0L)

    /** Sends the world and the player state to the client. */
    fun send(context: StageContext) {
        val connection = context.connection
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

        SpawnChunks.send(context)

        context.logger.info(
            "[${connection.getIp()}] Player ${connection.session.username} joined the void (entity $entityId)",
        )
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
