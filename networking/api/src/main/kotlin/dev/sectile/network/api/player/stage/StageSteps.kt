package dev.sectile.network.api.player.stage

/**
 * The extension points of the stage machine.
 *
 * Every step of the lifecycle has a canonical name. A plugin registers a hook on one of them
 * with [StageHooks.before] or [StageHooks.after], and the server uses these constants so a
 * renamed step breaks the compilation instead of the hook.
 */
object StageSteps {

    /** HANDSHAKE: accepts the `Connect` packet. */
    const val HANDSHAKE_CONNECT_ACCEPTANCE = "connect-acceptance"

    /** HANDSHAKE: checks that the client speaks the same protocol as the server. */
    const val HANDSHAKE_PROTOCOL_COMPATIBILITY = "protocol-compatibility"

    /** HANDSHAKE: validates the optional referral payload. */
    const val HANDSHAKE_REFERRAL_DATA = "referral-data"

    /** HANDSHAKE: chooses the login flow and moves to the next stage. */
    const val HANDSHAKE_LOGIN_FLOW = "login-flow"

    /** AUTHENTICATION: validates the identity token sent by the client. */
    const val AUTHENTICATION_IDENTITY_TOKEN = "identity-token"

    /** AUTHENTICATION: asks the Session Service for an authorization grant. */
    const val AUTHENTICATION_AUTHORIZATION_GRANT = "authorization-grant"

    /** AUTHENTICATION: validates the access token sent by the client. */
    const val AUTHENTICATION_ACCESS_TOKEN = "access-token"

    /** AUTHENTICATION: exchanges the grant of the client for an access token of the server. */
    const val AUTHENTICATION_SERVER_AUTH_TOKEN = "server-auth-token"

    /** INSECURE_OPTIONS: asks the client for the options of its player. */
    const val INSECURE_OPTIONS_REQUEST_PLAYER_OPTIONS = "request-player-options"

    /** INSECURE_OPTIONS: stores the identity the client wants to use. */
    const val INSECURE_OPTIONS_PLAYER_IDENTITY = "player-identity"

    /** INSECURE_OPTIONS: sends the password challenge. */
    const val INSECURE_OPTIONS_PASSWORD_CHALLENGE = "password-challenge"

    /** PASSWORD: checks that the client answered a challenge that was issued. */
    const val PASSWORD_CHALLENGE_VALIDATION = "challenge-validation"

    /** PASSWORD: compares the hash of the client with the expected one. */
    const val PASSWORD_VERIFICATION = "password-verification"

    /** PASSWORD: accepts the password. */
    const val PASSWORD_ACCEPT = "password-accept"

    /**
     * Builds the identity of the player and hands the connection over to the world setup.
     *
     * It runs in the stage that completed the login: `AUTHENTICATION`, `INSECURE_OPTIONS` or
     * `PASSWORD`.
     */
    const val LOGIN_COMPLETION = "login-completion"

    /** PRE_LOGIN: starts the session of the player. */
    const val PRE_LOGIN_SESSION_START = "session-start"

    /** PRE_LOGIN: notifies that the login finished. */
    const val PRE_LOGIN_LOGIN_NOTIFY = "login-notify"

    /** PRE_LOGIN: sends the settings of the world and the information of the server. */
    const val PRE_LOGIN_WORLD_SETTINGS = "world-settings"

    /** PRE_LOGIN: arms the fallback that delivers the assets. */
    const val PRE_LOGIN_ASSET_LOADING = "asset-loading"

    /** PRE_LOGIN: delivers the assets the client needs. */
    const val PRE_LOGIN_ASSET_DELIVERY = "asset-delivery"

    /** PRE_LOGIN: moves to the gameplay stage and sends the state of the world. */
    const val PRE_LOGIN_SETUP_FINALIZE = "setup-finalize"

    /** POST_LOGIN: answers the `ClientReady` packet of the client. */
    const val POST_LOGIN_CLIENT_READY = "client-ready"

    /** POST_LOGIN: starts the tasks that keep the connection alive. */
    const val POST_LOGIN_NETWORK_TASKS = "network-tasks"
}
