package dev.sectile.network.internal.stages

import dev.sectile.network.api.player.SimplePlayerAuthentication
import dev.sectile.network.api.player.connection.ConnectionStage
import dev.sectile.network.api.player.stage.StageSteps
import java.util.UUID

/**
 * Builds the identity of the player and hands the connection over to the world setup.
 *
 * This is the last step of every login flow, no matter whether the player had to
 * authenticate, to pick a name or to type a password. It runs in the stage that completed the
 * login (`AUTHENTICATION`, `INSECURE_OPTIONS` or `PASSWORD`).
 */
internal object LoginCompletionStep : StageStep {

    override val name: String = StageSteps.LOGIN_COMPLETION

    /**
     * Completes the login of [context].
     *
     * The transition to `PRE_LOGIN` is only requested: it is committed when the step finishes,
     * so the extensions still get the chance to reject the connection.
     */
    fun complete(context: StageContext) {
        val connection = context.connection
        val state = context.login

        val playerUuid = state.uuid ?: UUID(0L, 0L)
        val playerName = state.username ?: ""
        val authentication = SimplePlayerAuthentication(
            uuid = playerUuid,
            username = playerName,
            identityToken = state.identityToken,
            accessToken = state.accessToken,
            authenticated = context.config.auth.authenticated,
        )

        context.logger.info("[${connection.getIp()}] Login complete for $playerName ($playerUuid)")

        // The connection is a player from now on: the identity of the session is set.
        connection.beginSession(authentication, context.environment.nextEntityId())
        context.requestStage(ConnectionStage.PRE_LOGIN)
    }
}

/**
 * Notifies that the login finished.
 *
 * It is the first entry step of `PRE_LOGIN`: the hook runs with the connection already in the
 * world setup stage, so a plugin can register handlers for it right away.
 */
internal object LoginNotifyStep : StageEntryStep {

    override val name: String = StageSteps.PRE_LOGIN_LOGIN_NOTIFY

    override fun run(context: StageContext, next: () -> Unit) {
        val authentication = context.connection.authentication
        if (authentication != null) {
        }

        next()
    }
}
