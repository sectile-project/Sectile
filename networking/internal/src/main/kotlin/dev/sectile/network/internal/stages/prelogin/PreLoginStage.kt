package dev.sectile.network.internal.stages.prelogin

import dev.sectile.network.api.packet.handler.PacketHandlerStorage
import dev.sectile.network.api.packet.handler.on
import dev.sectile.network.api.player.connection.ConnectionStage
import dev.sectile.network.api.player.stage.StageSteps
import dev.sectile.network.internal.stages.LoginNotifyStep
import dev.sectile.network.internal.stages.Stage
import dev.sectile.network.internal.stages.StageContext
import dev.sectile.network.internal.stages.StageEntryStep
import dev.sectile.network.internal.stages.StageEnvironment
import dev.sectile.network.internal.stages.StagePacketStep
import dev.sectile.network.internal.stages.onClientDisconnect
import dev.sectile.network.internal.stages.onViewRadius
import dev.sectile.network.internal.stages.prelogin.steps.AssetDeliveryStep
import dev.sectile.network.internal.stages.prelogin.steps.AssetLoadingStep
import dev.sectile.network.internal.stages.prelogin.steps.SessionStartStep
import dev.sectile.network.internal.stages.prelogin.steps.SetupFinalizeStep
import dev.sectile.network.internal.stages.prelogin.steps.WorldSettingsStep
import dev.sectile.network.protocol.ProtocolPacketTable
import dev.sectile.network.protocol.setup.RequestAssetsPacket
import dev.sectile.network.protocol.setup.SetupFinalizePacket
import java.time.Duration

/**
 * PRE_LOGIN stage.
 *
 * **Entered when** the login completes. The player is authenticated but the world is not ready
 * yet.
 *
 * **What happens on entry**
 *  1. `session-start` - starts the session of the player and closes the connection cleanly when
 *     the player leaves.
 *  2. `login-notify` - notifies that the login finished.
 *  3. `world-settings` - answers with `WorldSettings` (the assets the client needs) and
 *     `ServerInfo` (the name, the MOTD and the capacity of the server).
 *  4. `asset-loading` - arms the fallback that delivers the assets when the client never asks
 *     for them.
 *
 * **Waits for**
 *  - `RequestAssets`: the client asks for the assets it is missing. The server answers with
 *    every asset type, the progress of the load and the end of the load.
 *  - `SetupFinalize`: the client finished loading. The server moves to `POST_LOGIN`, opens the
 *    auxiliary streams and sends the state of the world.
 *
 * **Exits to** `POST_LOGIN`.
 */
internal object PreLoginStage : Stage {

    override val stage: ConnectionStage = ConnectionStage.PRE_LOGIN

    /** The session stages have no deadline of their own: the play timeout is used. */
    override val timeout: Duration? = null

    override val entrySteps: List<StageEntryStep> = listOf(
        SessionStartStep,
        LoginNotifyStep,
        WorldSettingsStep,
        AssetLoadingStep,
    )

    override fun register(storage: PacketHandlerStorage, environment: StageEnvironment) {
        val assetSteps = listOf<StagePacketStep<RequestAssetsPacket>>(AssetDeliveryStep)
        val finalizeSteps = listOf<StagePacketStep<SetupFinalizePacket>>(SetupFinalizeStep)

        storage.on(ProtocolPacketTable.REQUEST_ASSETS, ::RequestAssetsPacket) { connection, packet ->
            val context = StageContext.of(connection, environment)
            context.runPacketSteps(stage, packet, assetSteps)
        }

        storage.on(ProtocolPacketTable.SETUP_FINALIZE, ::SetupFinalizePacket) { connection, packet ->
            val context = StageContext.of(connection, environment)
            context.runPacketSteps(stage, packet, finalizeSteps)
        }

        storage.onViewRadius()
        storage.onClientDisconnect(environment)
    }
}
