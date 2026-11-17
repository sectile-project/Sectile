package dev.sectile.network.internal.stages.authentication

import dev.sectile.network.api.packet.handler.PacketHandlerStorage
import dev.sectile.network.api.packet.handler.on
import dev.sectile.network.api.player.connection.ConnectionStage
import dev.sectile.network.internal.stages.Stage
import dev.sectile.network.internal.stages.StageContext
import dev.sectile.network.internal.stages.StageEntryStep
import dev.sectile.network.internal.stages.StageEnvironment
import dev.sectile.network.internal.stages.authentication.steps.AccessTokenStep
import dev.sectile.network.internal.stages.authentication.steps.AuthorizationGrantStep
import dev.sectile.network.internal.stages.authentication.steps.IdentityTokenStep
import dev.sectile.network.internal.stages.authentication.steps.ServerAuthTokenStep
import dev.sectile.network.internal.stages.onClientDisconnect
import dev.sectile.network.protocol.ProtocolPacketTable
import dev.sectile.network.protocol.auth.AuthTokenPacket
import java.time.Duration

/**
 * AUTHENTICATION stage (authenticated mode only).
 *
 * **Entered when** the handshake succeeded in a server configured with
 * [dev.sectile.network.internal.config.AuthMode.AUTHENTICATED].
 *
 * **What happens on entry**
 *  1. `identity-token` - validates the identity token the client sent in the `Connect` packet:
 *     signature, issuer, dates and scope.
 *  2. `authorization-grant` - asks the Session Service for an authorization grant and sends it
 *     to the client inside `AuthGrant`.
 *
 * **Waits for** the `AuthToken` packet, the answer of the client to the grant.
 *
 * **What happens on that packet**
 *  3. `access-token` - validates the access token of the client and the authorization grant it
 *     sends back.
 *  4. `server-auth-token` - exchanges that grant for a server access token and answers with
 *     `ServerAuthToken`, which also carries the password challenge.
 *
 * **Exits to** `PASSWORD` when the server asks for a password, or to `PRE_LOGIN` when it does
 * not.
 */
internal object AuthenticationStage : Stage {

    override val stage: ConnectionStage = ConnectionStage.AUTHENTICATION

    /** The client has 30 seconds to answer the grant with its access token. */
    override val timeout: Duration = Duration.ofSeconds(30)

    override val readTimeoutGrace: Duration = Duration.ofSeconds(5)

    override val entrySteps: List<StageEntryStep> = listOf(
        IdentityTokenStep,
        AuthorizationGrantStep,
    )

    override fun register(storage: PacketHandlerStorage, environment: StageEnvironment) {
        val steps = listOf(
            AccessTokenStep,
            ServerAuthTokenStep,
        )

        storage.on(ProtocolPacketTable.AUTH_TOKEN, ::AuthTokenPacket) { connection, packet ->
            val context = StageContext.of(connection, environment)
            context.runPacketSteps(stage, packet, steps)
        }

        storage.onClientDisconnect(environment, " during login")
    }
}
