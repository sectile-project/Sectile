package dev.sectile.network.internal.stages.authentication.steps

import dev.sectile.network.api.player.connection.ConnectionStage
import dev.sectile.network.api.player.stage.StageSteps
import dev.sectile.network.internal.auth.NetworkAuth
import dev.sectile.network.internal.stages.StageContext
import dev.sectile.network.internal.stages.StageEntryStep
import dev.sectile.network.protocol.auth.AuthGrantPacket

/**
 * Requests an authorization grant for the player that is connecting.
 *
 * The Session Service answers with a grant that proves the player may join this server. The
 * grant is sent to the client inside `AuthGrant`, together with the identity token of the
 * server, so the client can complete the mutual authentication.
 */
internal object AuthorizationGrantStep : StageEntryStep {

    override val name: String = StageSteps.AUTHENTICATION_AUTHORIZATION_GRANT

    override fun run(context: StageContext, next: () -> Unit) {
        val sessionService = NetworkAuth.sessionService()
        if (sessionService == null) {
            context.logger.error("Authentication is required but the Session Service is not configured")
            context.reject("client.general.disconnect.serverAuthUnavailable")
            return
        }

        val identityToken = context.login.identityToken ?: ""
        val audience = context.config.auth.audience ?: ""
        val serverSessionToken = NetworkAuth.sessionToken ?: ""
        val serverIdentityToken = NetworkAuth.identityToken

        context.environment.authExecutor.execute {
            context.logger.info("[${context.connection.getIp()}] Requesting authorization grant (audience=$audience)")

            val grant = sessionService.requestAuthorizationGrant(identityToken, audience, serverSessionToken)
            if (grant.isNullOrEmpty()) {
                context.logger.warn(
                    "The Session Service did not return an authorization grant. If the error was " +
                        "'invalid token', the server session token has expired (it lasts one hour): " +
                        "restart the server to authenticate again.",
                )
                context.connection.execute {
                    context.reject("client.general.disconnect.authGrantFailed")
                }
                return@execute
            }

            if (serverIdentityToken.isNullOrEmpty()) {
                context.connection.execute {
                    context.reject("client.general.disconnect.serverAuthUnavailable")
                }
                return@execute
            }

            context.connection.execute {
                if (!context.connection.isOnline() || context.connection.stage != ConnectionStage.AUTHENTICATION) {
                    return@execute
                }

                context.logger.info(
                    "[${context.connection.getIp()}] Sending AuthGrant to ${context.login.username ?: "player"} " +
                        "(grant: ${grant.length} chars, server identity token: ${serverIdentityToken.length} chars)",
                )
                context.connection.sendPacket(AuthGrantPacket(grant, serverIdentityToken))

                next()
            }
        }
    }
}
