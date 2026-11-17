package dev.sectile.network.internal.stages.authentication.steps

import dev.sectile.network.api.player.connection.ConnectionStage
import dev.sectile.network.api.player.stage.StageSteps
import dev.sectile.network.internal.auth.NetworkAuth
import dev.sectile.network.internal.stages.LoginCompletionStep
import dev.sectile.network.internal.stages.PasswordChallenge
import dev.sectile.network.internal.stages.StageContext
import dev.sectile.network.internal.stages.StagePacketStep
import dev.sectile.network.protocol.auth.AuthTokenPacket
import dev.sectile.network.protocol.auth.ServerAuthTokenPacket

/**
 * Exchanges the grant of the client for an access token of the server.
 *
 * The answer is sent inside `ServerAuthToken`, together with the password challenge. It is the
 * last step of the mutual authentication: the client knows the server is the real one, and the
 * server knows the client is too.
 */
internal object ServerAuthTokenStep : StagePacketStep<AuthTokenPacket> {

    override val name: String = StageSteps.AUTHENTICATION_SERVER_AUTH_TOKEN

    override fun run(context: StageContext, packet: AuthTokenPacket, next: () -> Unit) {
        val sessionService = NetworkAuth.sessionService()
        if (sessionService == null) {
            context.reject("client.general.disconnect.serverAuthUnavailable")
            return
        }

        val serverFingerprint = NetworkAuth.serverCertificateFingerprint
        if (serverFingerprint == null) {
            context.reject("client.general.disconnect.serverAuthUnavailable")
            return
        }

        val serverAuthGrant = packet.serverAuthGrant ?: ""
        val serverSessionToken = NetworkAuth.sessionToken ?: ""
        val password = context.config.auth.password

        context.environment.authExecutor.execute {
            val serverAccessToken = sessionService.exchangeAuthGrantForToken(
                serverAuthGrant,
                serverFingerprint,
                serverSessionToken,
            )
            if (serverAccessToken.isNullOrEmpty()) {
                context.logger.warn("[${context.connection.getIp()}] The Session Service did not return a server access token")
                context.connection.execute {
                    context.reject("client.general.disconnect.serverAuthFailed")
                }
                return@execute
            }

            // The challenge only exists when the server asks for a password.
            val challenge = PasswordChallenge.create(password)

            context.connection.execute {
                if (!context.connection.isOnline() || context.connection.stage != ConnectionStage.AUTHENTICATION) {
                    return@execute
                }

                context.login.passwordChallenge = challenge
                context.logger.info(
                    "[${context.connection.getIp()}] Sending ServerAuthToken to ${context.login.username ?: "player"}",
                )
                context.connection.sendPacket(ServerAuthTokenPacket(serverAccessToken, challenge))

                if (challenge == null) {
                    // The server does not ask for a password: the login is complete.
                    LoginCompletionStep.complete(context)
                } else {
                    context.requestStage(ConnectionStage.PASSWORD)
                }

                next()
            }
        }
    }
}
