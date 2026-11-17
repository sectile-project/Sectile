package dev.sectile.network.internal.stages.authentication.steps

import dev.sectile.network.api.player.connection.ConnectionStage
import dev.sectile.network.api.player.stage.StageSteps
import dev.sectile.network.internal.auth.NetworkAuth
import dev.sectile.network.internal.stages.StageContext
import dev.sectile.network.internal.stages.StagePacketStep
import dev.sectile.network.protocol.auth.AuthTokenPacket

/**
 * Validates the access token the client sent in the `AuthToken` packet.
 *
 * The access token is bound to the certificate the client presented during the QUIC
 * handshake, and it must describe the same player as the identity token: otherwise the client
 * is trying to impersonate somebody else.
 */
internal object AccessTokenStep : StagePacketStep<AuthTokenPacket> {

    override val name: String = StageSteps.AUTHENTICATION_ACCESS_TOKEN

    override fun run(context: StageContext, packet: AuthTokenPacket, next: () -> Unit) {
        val state = context.login
        if (state.authTokenReceived) {
            context.reject("client.general.disconnect.protocol.duplicateAuthToken")
            return
        }
        state.authTokenReceived = true

        context.logger.info(
            "[${context.connection.getIp()}] AuthToken received " +
                "(access token=${if (packet.accessToken.isNullOrEmpty()) "none" else "present"}, " +
                "server auth grant=${if (packet.serverAuthGrant.isNullOrEmpty()) "none" else "present"})",
        )

        val token = packet.accessToken
        if (token.isNullOrEmpty()) {
            context.reject("client.general.disconnect.invalidAccessToken")
            return
        }
        state.accessToken = token

        val serverAuthGrant = packet.serverAuthGrant
        if (serverAuthGrant.isNullOrEmpty()) {
            context.logger.warn(
                "[${context.connection.getIp()}] Client did not provide a server auth grant " +
                    "for mutual authentication",
            )
            context.reject("client.general.disconnect.mutualAuthRequired")
            return
        }

        val validator = NetworkAuth.validator()
        if (validator == null || NetworkAuth.sessionService() == null) {
            context.reject("client.general.disconnect.serverAuthUnavailable")
            return
        }

        val clientCertificate = context.connection.clientCertificate
        val identitySubject = state.identitySubject
        val username = state.username

        context.environment.authExecutor.execute {
            val claims = validator.validateAccessToken(token, clientCertificate)
            if (claims == null) {
                context.connection.execute {
                    context.reject("client.general.disconnect.invalidAccessToken")
                }
                return@execute
            }

            context.logger.info(
                "[${context.connection.getIp()}] Access token validated for ${claims.username} (${claims.subject})",
            )

            context.connection.execute {
                if (!context.connection.isOnline() || context.connection.stage != ConnectionStage.AUTHENTICATION) {
                    return@execute
                }

                if (claims.subject != identitySubject) {
                    context.reject("client.general.disconnect.tokenUuidMismatch")
                    return@execute
                }

                if (claims.username.isNullOrEmpty()) {
                    context.reject("client.general.disconnect.tokenMissingUsername")
                    return@execute
                }

                if (claims.username != username) {
                    context.reject("client.general.disconnect.tokenUsernameMismatch")
                    return@execute
                }

                next()
            }
        }
    }
}
