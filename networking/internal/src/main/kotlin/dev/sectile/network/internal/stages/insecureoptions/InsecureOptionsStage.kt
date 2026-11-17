package dev.sectile.network.internal.stages.insecureoptions

import dev.sectile.network.api.packet.handler.PacketHandlerStorage
import dev.sectile.network.api.packet.handler.on
import dev.sectile.network.api.player.connection.ConnectionStage
import dev.sectile.network.api.player.stage.StageSteps
import dev.sectile.network.internal.stages.LoginCompletionStep
import dev.sectile.network.internal.stages.PasswordChallenge
import dev.sectile.network.internal.stages.Stage
import dev.sectile.network.internal.stages.StageContext
import dev.sectile.network.internal.stages.StageEntryStep
import dev.sectile.network.internal.stages.StageEnvironment
import dev.sectile.network.internal.stages.StagePacketStep
import dev.sectile.network.internal.stages.onClientDisconnect
import dev.sectile.network.protocol.ProtocolPacketTable
import dev.sectile.network.protocol.auth.ConnectAcceptPacket
import dev.sectile.network.protocol.connection.InsecurePlayerOptionsPacket
import dev.sectile.network.protocol.connection.RequestInsecurePlayerOptionsPacket
import java.time.Duration

/**
 * INSECURE_OPTIONS stage (insecure mode only).
 *
 * **Entered when** the handshake succeeded in a server configured with
 * [dev.sectile.network.internal.config.AuthMode.INSECURE]. The server does not validate the
 * identity of the player: it only needs to know who the player wants to be.
 *
 * **What happens on entry**
 *  1. `request-player-options` - asks the client for the options of its player.
 *
 * **Waits for** the `InsecurePlayerOptions` packet, which carries the UUID, the username and
 * the skin of the player.
 *
 * **What happens on that packet**
 *  2. `player-identity` - checks that the handshake already happened and that the client sent a
 *     username, and stores the identity of the player.
 *  3. `password-challenge` - answers with `ConnectAccept`, which carries the password
 *     challenge, and moves to `PASSWORD`. When the server does not ask for a password the
 *     login is completed right away.
 *
 * **Exits to** `PASSWORD` or `PRE_LOGIN`.
 */
internal object InsecureOptionsStage : Stage {

    override val stage: ConnectionStage = ConnectionStage.INSECURE_OPTIONS

    /** The client has 20 seconds to send its player options. */
    override val timeout: Duration = Duration.ofSeconds(20)

    override val readTimeoutGrace: Duration = Duration.ofSeconds(5)

    override val entrySteps: List<StageEntryStep> = listOf(
        RequestPlayerOptionsStep,
    )

    override fun register(storage: PacketHandlerStorage, environment: StageEnvironment) {
        val steps = listOf(
            PlayerIdentityStep,
            PasswordChallengeStep,
        )

        storage.on(ProtocolPacketTable.INSECURE_PLAYER_OPTIONS, ::InsecurePlayerOptionsPacket) { connection, packet ->
            val context = StageContext.of(connection, environment)
            context.runPacketSteps(stage, packet, steps)
        }

        storage.onClientDisconnect(environment, " during login")
    }
}

/**
 * Asks the client for its player options.
 *
 * In insecure mode the client picks its own identity, so the server has to ask for it: the
 * answer comes in the `InsecurePlayerOptions` packet, which is the only packet this stage
 * waits for.
 */
private object RequestPlayerOptionsStep : StageEntryStep {

    override val name: String = StageSteps.INSECURE_OPTIONS_REQUEST_PLAYER_OPTIONS

    override fun run(context: StageContext, next: () -> Unit) {
        context.connection.sendPacket(RequestInsecurePlayerOptionsPacket())

        next()
    }
}

/**
 * Stores the identity the client wants to use.
 *
 * In insecure mode the client picks its own UUID and username: the server only checks that the
 * handshake already happened and that the username is not empty.
 */
private object PlayerIdentityStep : StagePacketStep<InsecurePlayerOptionsPacket> {

    override val name: String = StageSteps.INSECURE_OPTIONS_PLAYER_IDENTITY

    override fun run(context: StageContext, packet: InsecurePlayerOptionsPacket, next: () -> Unit) {
        val state = context.login
        if (!state.connectReceived) {
            context.reject("client.general.disconnect.protocol.unexpectedPacket")
            return
        }

        if (packet.username.isEmpty()) {
            context.reject("client.general.disconnect.missingUsername")
            return
        }

        state.uuid = packet.uuid
        state.username = packet.username

        next()
    }
}

/**
 * Sends the password challenge and moves to the password stage.
 *
 * `ConnectAccept` tells the client that the server accepted its options. It also carries the
 * challenge the client has to hash together with the password. When the server does not ask
 * for a password there is no challenge and the login is complete.
 */
private object PasswordChallengeStep : StagePacketStep<InsecurePlayerOptionsPacket> {

    override val name: String = StageSteps.INSECURE_OPTIONS_PASSWORD_CHALLENGE

    override fun run(context: StageContext, packet: InsecurePlayerOptionsPacket, next: () -> Unit) {
        val challenge = PasswordChallenge.create(context.config.auth.password)
        context.login.passwordChallenge = challenge

        context.connection.sendPacket(ConnectAcceptPacket(challenge))

        if (challenge == null) {
            // The server does not ask for a password: the login is complete.
            LoginCompletionStep.complete(context)
        } else {
            context.requestStage(ConnectionStage.PASSWORD)
        }

        next()
    }
}
