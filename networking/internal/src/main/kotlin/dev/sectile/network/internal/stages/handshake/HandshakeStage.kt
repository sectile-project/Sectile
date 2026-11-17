package dev.sectile.network.internal.stages.handshake

import dev.sectile.network.api.packet.handler.PacketHandlerStorage
import dev.sectile.network.api.packet.handler.on
import dev.sectile.network.api.player.connection.ConnectionCloseCode
import dev.sectile.network.api.player.connection.ConnectionStage
import dev.sectile.network.api.player.stage.StageSteps
import dev.sectile.network.internal.auth.NetworkAuth
import dev.sectile.network.internal.stages.Stage
import dev.sectile.network.internal.stages.StageContext
import dev.sectile.network.internal.stages.StageEnvironment
import dev.sectile.network.internal.stages.StagePacketStep
import dev.sectile.network.internal.stages.onClientDisconnect
import dev.sectile.network.protocol.ProtocolPacketTable
import dev.sectile.network.protocol.ProtocolSettings
import dev.sectile.network.protocol.connection.ConnectPacket
import java.time.Duration

/**
 * HANDSHAKE stage.
 *
 * **Entered when** the QUIC connection is established. Nothing is known about the client yet.
 *
 * **Waits for** the `Connect` packet, the only packet accepted in this stage. It describes who
 * the client is (client type, language, identity token) and which protocol it speaks.
 *
 * **What happens inside**
 *  1. `connect-acceptance` - makes sure this is the first `Connect` and logs who the client is.
 *  2. `protocol-compatibility` - rejects the clients whose protocol does not match the server.
 *  3. `referral-data` - validates the optional referral payload.
 *  4. `login-flow` - chooses the login flow and moves to the stage that implements it.
 *
 * **Exits to** `INSECURE_OPTIONS` (insecure mode) or `AUTHENTICATION` (authenticated mode).
 */
internal object HandshakeStage : Stage {

    override val stage: ConnectionStage = ConnectionStage.HANDSHAKE

    /**
     * The handshake has no deadline of its own: the connection is created with the read timeout
     * of the server configuration, and that timeout is the limit of this stage.
     */
    override val timeout: Duration? = null

    override fun register(storage: PacketHandlerStorage, environment: StageEnvironment) {
        val steps = listOf(
            ConnectAcceptanceStep,
            ProtocolCompatibilityStep,
            ReferralDataStep,
            LoginFlowStep,
        )

        storage.on(ProtocolPacketTable.CONNECT, ::ConnectPacket) { connection, packet ->
            val context = StageContext.of(connection, environment)
            context.runPacketSteps(stage, packet, steps)
        }

        storage.onClientDisconnect(environment, " during login")
    }
}

/**
 * Accepts the `Connect` packet.
 *
 * The `Connect` packet is the first thing the client sends, so it is accepted only once: a
 * second one is a protocol violation.
 */
private object ConnectAcceptanceStep : StagePacketStep<ConnectPacket> {

    override val name: String = StageSteps.HANDSHAKE_CONNECT_ACCEPTANCE

    override fun run(context: StageContext, packet: ConnectPacket, next: () -> Unit) {
        val state = context.login
        if (state.connectReceived) {
            context.reject("client.general.disconnect.protocol.unexpectedPacket")
            return
        }
        state.connectReceived = true

        context.logger.info(
            "[${context.connection.getIp()}] Connect received (client type=${packet.clientType}, " +
                "identity token=${if (packet.identityToken.isNullOrEmpty()) "none" else "present"}, " +
                "language=${packet.language}, referral=${packet.referralData != null})",
        )

        next()
    }
}

/**
 * Rejects the clients that do not speak the same protocol as the server.
 *
 * The client sends the CRC of the protocol it was built with. A mismatch means both sides
 * disagree on the layout of the packets, so the connection is closed right away with
 * `CLIENT_OUTDATED` or `SERVER_OUTDATED`, depending on which side is behind.
 */
private object ProtocolCompatibilityStep : StagePacketStep<ConnectPacket> {

    override val name: String = StageSteps.HANDSHAKE_PROTOCOL_COMPATIBILITY

    override fun run(context: StageContext, packet: ConnectPacket, next: () -> Unit) {
        if (ProtocolSettings.validateCrc(packet.protocolCrc)) {
            next()
            return
        }

        val clientBuild = packet.protocolBuildNumber
        val serverBuild = ProtocolSettings.PROTOCOL_BUILD_NUMBER
        val outdated = clientBuild < serverBuild
        val code = if (outdated) ConnectionCloseCode.CLIENT_OUTDATED else ConnectionCloseCode.SERVER_OUTDATED
        val reason = if (outdated) {
            "client.general.disconnect.clientOutdated"
        } else {
            "client.general.disconnect.serverOutdated"
        }

        context.logger.warn(
            "[${context.connection.getIp()}] Rejecting connection: protocol CRC mismatch " +
                "(client build $clientBuild, server build $serverBuild).",
        )
        context.reject(code, reason)
    }
}

/**
 * Validates the optional referral payload sent by the client.
 *
 * The referral data is a blob the client received from another server. It is capped and it
 * must come with a valid source, otherwise the connection is closed.
 */
private object ReferralDataStep : StagePacketStep<ConnectPacket> {

    override val name: String = StageSteps.HANDSHAKE_REFERRAL_DATA

    /** Maximum size of the referral payload, in bytes. */
    private const val MAX_REFERRAL_DATA_SIZE = 4096

    override fun run(context: StageContext, packet: ConnectPacket, next: () -> Unit) {
        val referralData = packet.referralData
        if (referralData == null) {
            next()
            return
        }

        if (referralData.size > MAX_REFERRAL_DATA_SIZE) {
            context.reject("client.general.disconnect.referralDataTooLarge")
            return
        }

        val source = packet.referralSource
        if (source == null) {
            context.reject("client.general.disconnect.referralMissingSource")
            return
        }

        if (source.host.isEmpty()) {
            context.reject("client.general.disconnect.referralInvalidSource")
            return
        }

        next()
    }
}

/**
 * Chooses the login flow and moves the connection to the stage that implements it.
 *
 *  - **Insecure mode**: the identity of the player is not validated. The connection moves to
 *    `INSECURE_OPTIONS`, where the server asks the client for its player options.
 *  - **Authenticated mode**: the client has to prove who it is. The connection moves to
 *    `AUTHENTICATION`, where the identity token sent in the `Connect` packet is validated and
 *    the mutual authentication is completed.
 */
private object LoginFlowStep : StagePacketStep<ConnectPacket> {

    override val name: String = StageSteps.HANDSHAKE_LOGIN_FLOW

    override fun run(context: StageContext, packet: ConnectPacket, next: () -> Unit) {
        if (!context.config.auth.authenticated) {
            context.requestStage(ConnectionStage.INSECURE_OPTIONS)
            next()
            return
        }

        val token = packet.identityToken
        if (token.isNullOrEmpty()) {
            context.logger.warn(
                "[${context.connection.getIp()}] Rejecting connection: server requires authentication " +
                    "and no identity token was sent",
            )
            context.reject("client.general.disconnect.serverRequiresAuthentication")
            return
        }

        if (NetworkAuth.validator() == null || NetworkAuth.sessionService() == null) {
            context.logger.error("Authentication is required but the Session Service is not configured")
            context.reject("client.general.disconnect.serverAuthUnavailable")
            return
        }

        // The identity token is validated later, inside the AUTHENTICATION stage.
        context.login.identityToken = token
        context.login.clientType = packet.clientType

        context.requestStage(ConnectionStage.AUTHENTICATION)
        next()
    }
}
