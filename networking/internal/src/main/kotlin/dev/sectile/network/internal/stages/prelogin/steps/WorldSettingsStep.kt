package dev.sectile.network.internal.stages.prelogin.steps

import dev.sectile.network.api.player.stage.StageSteps
import dev.sectile.network.internal.stages.StageContext
import dev.sectile.network.internal.stages.StageEntryStep
import dev.sectile.network.protocol.assets.DefaultAssetPayloads
import dev.sectile.network.protocol.interfaceui.ServerInfoPacket
import dev.sectile.network.protocol.setup.WorldSettingsPacket

/**
 * Sends the settings of the world and the information of the server.
 *
 * `WorldSettings` carries the assets the client needs before loading the world, and
 * `ServerInfo` the name, the MOTD and the capacity of the server.
 */
internal object WorldSettingsStep : StageEntryStep {

    override val name: String = StageSteps.PRE_LOGIN_WORLD_SETTINGS

    override fun run(context: StageContext, next: () -> Unit) {
        val config = context.config
        context.connection.sendPackets(
            WorldSettingsPacket(DefaultAssetPayloads.REQUIRED_COMMON_ASSETS.toMutableList()),
            ServerInfoPacket(
                serverName = config.serverName,
                motd = config.motd,
                maxPlayers = config.maxPlayers,
            ),
        )

        next()
    }
}
