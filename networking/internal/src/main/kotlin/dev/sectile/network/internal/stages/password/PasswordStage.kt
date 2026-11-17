package dev.sectile.network.internal.stages.password

import dev.sectile.network.api.packet.handler.PacketHandlerStorage
import dev.sectile.network.api.packet.handler.on
import dev.sectile.network.api.player.connection.ConnectionStage
import dev.sectile.network.api.player.stage.StageSteps
import dev.sectile.network.internal.stages.LoginCompletionStep
import dev.sectile.network.internal.stages.PasswordChallenge
import dev.sectile.network.internal.stages.Stage
import dev.sectile.network.internal.stages.StageContext
import dev.sectile.network.internal.stages.StageEnvironment
import dev.sectile.network.internal.stages.StagePacketStep
import dev.sectile.network.internal.stages.onClientDisconnect
import dev.sectile.network.protocol.ProtocolPacketTable
import dev.sectile.network.protocol.auth.PasswordAcceptedPacket
import dev.sectile.network.protocol.auth.PasswordRejectedPacket
import dev.sectile.network.protocol.auth.PasswordResponsePacket
import java.security.MessageDigest
import java.time.Duration

/**
 * PASSWORD stage.
 *
 * **Entered when** the server asked for a password, in both login flows.
 *
 * **Waits for** the `PasswordResponse` packet, which carries the SHA-256 of the challenge
 * followed by the password.
 *
 * **What happens on that packet**
 *  1. `challenge-validation` - checks that a challenge was issued and that the client answered
 *     with a hash.
 *  2. `password-verification` - compares the hash of the client with the expected one. A wrong
 *     password costs one attempt and the client gets a new challenge; when the attempts run out
 *     the connection is closed.
 *  3. `password-accept` - answers with `PasswordAccepted` and completes the login.
 *
 * **Exits to** `PRE_LOGIN`.
 */
internal object PasswordStage : Stage {

    override val stage: ConnectionStage = ConnectionStage.PASSWORD

    /** The client has 45 seconds to answer the challenge. */
    override val timeout: Duration = Duration.ofSeconds(45)

    override val readTimeoutGrace: Duration = Duration.ofSeconds(5)

    override fun register(storage: PacketHandlerStorage, environment: StageEnvironment) {
        val steps = listOf(
            ChallengeValidationStep,
            PasswordVerificationStep,
            PasswordAcceptStep,
        )

        storage.on(ProtocolPacketTable.PASSWORD_RESPONSE, ::PasswordResponsePacket) { connection, packet ->
            val context = StageContext.of(connection, environment)
            context.runPacketSteps(stage, packet, steps)
        }

        storage.onClientDisconnect(environment, " during login")
    }
}

/**
 * Checks that the client answered a challenge that was actually issued.
 *
 * A response without a challenge is a protocol violation, and a response without a hash is an
 * invalid one.
 */
private object ChallengeValidationStep : StagePacketStep<PasswordResponsePacket> {

    override val name: String = StageSteps.PASSWORD_CHALLENGE_VALIDATION

    override fun run(context: StageContext, packet: PasswordResponsePacket, next: () -> Unit) {
        val challenge = context.login.passwordChallenge
        if (challenge == null || challenge.isEmpty()) {
            context.reject("client.general.disconnect.protocol.unexpectedPasswordResponse")
            return
        }

        val clientHash = packet.hash
        if (clientHash == null || clientHash.isEmpty()) {
            context.reject("client.general.disconnect.invalidPasswordResponse")
            return
        }

        val password = context.config.auth.password
        if (password.isNullOrEmpty()) {
            context.reject("client.general.disconnect.serverConfigError")
            return
        }

        next()
    }
}

/**
 * Compares the hash of the client with the expected one.
 *
 * A wrong password costs one attempt: the client is answered with `PasswordRejected`, which
 * carries the remaining attempts and a new challenge. When there are no attempts left the
 * connection is closed.
 */
private object PasswordVerificationStep : StagePacketStep<PasswordResponsePacket> {

    override val name: String = StageSteps.PASSWORD_VERIFICATION

    override fun run(context: StageContext, packet: PasswordResponsePacket, next: () -> Unit) {
        val state = context.login
        val challenge = state.passwordChallenge ?: return
        val clientHash = packet.hash ?: return
        val password = context.config.auth.password ?: return

        val expected = PasswordChallenge.expectedHash(challenge, password)
        if (MessageDigest.isEqual(expected, clientHash)) {
            next()
            return
        }

        state.attemptsRemaining--
        context.logger.warn(
            "[${context.connection.getIp()}] Invalid password, ${state.attemptsRemaining} attempts remaining",
        )

        if (state.attemptsRemaining <= 0) {
            context.reject("client.general.disconnect.tooManyPasswordAttempts")
            return
        }

        val newChallenge = PasswordChallenge.create(password) ?: return
        state.passwordChallenge = newChallenge
        context.connection.sendPacket(PasswordRejectedPacket(state.attemptsRemaining, newChallenge))
    }
}

/**
 * Accepts the password and completes the login.
 */
private object PasswordAcceptStep : StagePacketStep<PasswordResponsePacket> {

    override val name: String = StageSteps.PASSWORD_ACCEPT

    override fun run(context: StageContext, packet: PasswordResponsePacket, next: () -> Unit) {
        context.connection.sendPacket(PasswordAcceptedPacket())
        LoginCompletionStep.complete(context)

        next()
    }
}
