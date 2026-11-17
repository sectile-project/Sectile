package dev.sectile.network.internal.stages

import dev.sectile.network.protocol.enums.ClientType
import java.util.UUID

/**
 * Mutable state shared by the login stages.
 *
 * The login stages run one after the other (`HANDSHAKE` -> `AUTHENTICATION` /
 * `INSECURE_OPTIONS` -> `PASSWORD`), so the identity of the player and the password
 * challenge have to survive from one stage to the next.
 */
internal class LoginState {

    /** `true` once the `Connect` packet has been accepted. */
    var connectReceived: Boolean = false

    /** `true` once the `AuthToken` packet has been accepted. */
    var authTokenReceived: Boolean = false

    /** Type of the client that started the connection (the editor needs another scope). */
    var clientType: ClientType = ClientType.GAME

    /** Identity token sent by the client in the `Connect` packet. */
    var identityToken: String? = null

    /** Access token sent by the client in the `AuthToken` packet. */
    var accessToken: String? = null

    /** Subject (the UUID of the player) read from the identity token. */
    var identitySubject: String? = null

    var username: String? = null

    var uuid: UUID? = null

    /** Current password challenge, or `null` when the server does not ask for a password. */
    var passwordChallenge: ByteArray? = null

    /** Number of password attempts left before the connection is closed. */
    var attemptsRemaining: Int = 3
}
