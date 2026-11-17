package dev.sectile.network.api.player.connection

/**
 * Lifecycle stage of a [PlayerConnection].
 *
 * Each stage maintains its own [dev.sectile.network.api.packet.handler.PacketHandlerStorage],
 * ensuring that packets are only dispatched while valid for the connection's current phase.
 *
 * @property strict When `true`, inbound packets with no handler registered for this stage
 *           immediately close the connection as a protocol violation. When `false`, valid
 *           inbound packets without handlers are safely ignored.
 */
enum class ConnectionStage(val strict: Boolean) {
    /** Waiting for the initial `Connect` packet. */
    HANDSHAKE(strict = true),

    /** Authenticated handshake waiting for `AuthToken`. */
    AUTHENTICATION(strict = true),

    /** Insecure mode waiting for `InsecurePlayerOptions`. */
    INSECURE_OPTIONS(strict = true),

    /** Password challenge stage waiting for `PasswordResponse`. */
    PASSWORD(strict = true),

    /** Pre-login world and asset setup stage (`RequestAssets`, `SetupFinalize`, etc.). */
    PRE_LOGIN(strict = false),

    /** Post-login active gameplay stage (`ClientReady`, `ClientMovement`, chat, etc.). */
    POST_LOGIN(strict = false),
}
