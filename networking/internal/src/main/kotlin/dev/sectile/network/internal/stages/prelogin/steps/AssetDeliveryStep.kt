package dev.sectile.network.internal.stages.prelogin.steps

import dev.sectile.network.api.player.stage.StageSteps
import dev.sectile.network.internal.stages.SessionState
import dev.sectile.network.internal.stages.StageContext
import dev.sectile.network.internal.stages.StagePacketStep
import dev.sectile.network.protocol.assets.AssetTypes
import dev.sectile.network.protocol.setup.RequestAssetsPacket
import dev.sectile.network.protocol.setup.WorldLoadFinishedPacket
import dev.sectile.network.protocol.setup.WorldLoadProgressPacket
import dev.sectile.network.protocol.structures.FormattedMessage

/**
 * Delivers the assets the client needs to load the world.
 *
 * The client asks for the assets it is missing with `RequestAssets`. The server answers with
 * every asset type it knows, the progress of the load and the end of the load.
 */
internal object AssetDeliveryStep : StagePacketStep<RequestAssetsPacket> {

    override val name: String = StageSteps.PRE_LOGIN_ASSET_DELIVERY

    override fun run(context: StageContext, packet: RequestAssetsPacket, next: () -> Unit) {
        deliver(context)
        next()
    }

    /**
     * Sends every asset type and closes the loading of the world.
     *
     * It does nothing when the assets were already delivered.
     */
    fun deliver(context: StageContext) {
        val state = context.session
        if (state.phase != SessionState.Phase.ASSETS || !context.connection.isOnline()) return
        state.phase = SessionState.Phase.LOADING

        for (assetPacket in AssetTypes.ALL) {
            context.connection.sendPacket(assetPacket)
        }

        context.connection.sendPackets(
            WorldLoadProgressPacket(
                status = FormattedMessage().also { it.rawText = "client.general.worldLoad.loadingWorld" },
                percentComplete = 0,
                percentCompleteSubitem = 0,
            ),
            WorldLoadFinishedPacket(),
        )
    }
}
