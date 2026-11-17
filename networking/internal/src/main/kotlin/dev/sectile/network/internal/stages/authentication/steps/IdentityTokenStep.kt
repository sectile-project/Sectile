package dev.sectile.network.internal.stages.authentication.steps

import dev.sectile.network.api.player.connection.ConnectionStage
import dev.sectile.network.api.player.stage.StageSteps
import dev.sectile.network.internal.auth.NetworkAuth
import dev.sectile.network.internal.config.AuthConfig
import dev.sectile.network.internal.stages.StageContext
import dev.sectile.network.internal.stages.StageEntryStep
import dev.sectile.network.protocol.enums.ClientType
import java.util.UUID

/**
 * Validates the identity token the client sent in the `Connect` packet.
 *
 * The token is a JWT signed by the Session Service: its signature, its issuer and its dates
 * are checked, and it must carry the scope of the client type that is connecting (the editor
 * needs another scope than the game).
 *
 * The validation is a blocking call (the JWKS may have to be downloaded), so it runs on the
 * authentication executor and the result is delivered back on the connection event loop.
 */
internal object IdentityTokenStep : StageEntryStep {

    override val name: String = StageSteps.AUTHENTICATION_IDENTITY_TOKEN

    override fun run(context: StageContext, next: () -> Unit) {
        val token = context.login.identityToken
        if (token.isNullOrEmpty()) {
            context.logger.warn(
                "[${context.connection.getIp()}] Rejecting connection: server requires authentication " +
                    "and no identity token was sent",
            )
            context.reject("client.general.disconnect.serverRequiresAuthentication")
            return
        }

        val validator = NetworkAuth.validator()
        if (validator == null) {
            context.logger.error("Authentication is required but the Session Service is not configured")
            context.reject("client.general.disconnect.serverAuthUnavailable")
            return
        }

        val clientType = context.login.clientType

        context.environment.authExecutor.execute {
            val claims = validator.validateIdentityToken(token)
            if (claims == null) {
                context.logger.warn("[${context.connection.getIp()}] Identity token validation failed")
                context.connection.execute {
                    context.reject("client.general.disconnect.invalidIdentityToken")
                }
                return@execute
            }

            context.connection.execute {
                if (!context.connection.isOnline() || context.connection.stage != ConnectionStage.AUTHENTICATION) {
                    return@execute
                }

                context.logger.info(
                    "[${context.connection.getIp()}] Identity token validated for ${claims.username} " +
                        "(${claims.subject}, scope=${claims.scope})",
                )

                if (claims.subject.isNullOrEmpty() || claims.username.isNullOrEmpty()) {
                    context.reject("client.general.disconnect.invalidIdentityToken")
                    return@execute
                }

                val requiredScope = if (clientType == ClientType.EDITOR) {
                    AuthConfig.SCOPE_EDITOR
                } else {
                    AuthConfig.SCOPE_CLIENT
                }
                if (!claims.hasScope(requiredScope)) {
                    context.logger.warn(
                        "[${context.connection.getIp()}] Identity token missing required scope '$requiredScope'",
                    )
                    context.reject("client.general.disconnect.identityTokenMissingScope")
                    return@execute
                }

                // The identity of the player is known: it is used to build the session when the
                // login completes.
                context.login.identitySubject = claims.subject
                context.login.uuid = parseUuid(claims.subject)
                context.login.username = claims.username

                next()
            }
        }
    }

    /** Reads the UUID of the player from the subject of the token. */
    private fun parseUuid(value: String?): UUID? = try {
        if (value.isNullOrEmpty()) null else UUID.fromString(value)
    } catch (_: IllegalArgumentException) {
        null
    }
}
