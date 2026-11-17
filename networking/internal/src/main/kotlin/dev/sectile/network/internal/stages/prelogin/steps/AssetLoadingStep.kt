package dev.sectile.network.internal.stages.prelogin.steps

import dev.sectile.network.api.player.stage.StageSteps
import dev.sectile.network.internal.stages.StageContext
import dev.sectile.network.internal.stages.StageEntryStep
import java.time.Duration

/**
 * Arms the fallback that delivers the assets when the client never asks for them.
 *
 * Some clients skip the `RequestAssets` packet when the list of common assets is empty. The
 * fallback makes sure the server always answers with the assets, so the client never waits
 * forever for them.
 */
internal object AssetLoadingStep : StageEntryStep {

    override val name: String = StageSteps.PRE_LOGIN_ASSET_LOADING

    /** Time the client has to ask for the assets before the server sends them anyway. */
    private val ASSET_REQUEST_TIMEOUT: Duration = Duration.ofSeconds(5)

    override fun run(context: StageContext, next: () -> Unit) {
        context.connection.schedule(ASSET_REQUEST_TIMEOUT) {
            AssetDeliveryStep.deliver(context)
        }

        next()
    }
}
