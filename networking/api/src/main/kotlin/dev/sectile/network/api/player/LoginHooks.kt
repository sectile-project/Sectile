package dev.sectile.network.api.player

import dev.sectile.network.api.player.connection.PlayerConnection

/**
 * Lifecycle hooks for player authentication completion and failure.
 */
object LoginHooks {

    /** Invoked when a player completes authentication and login. */
    @Volatile
    var onLoginComplete: ((PlayerConnection, PlayerAuthentication) -> Unit)? = null

    /** Invoked when login fails, providing the failure reason or translation key. */
    @Volatile
    var onLoginFailed: ((PlayerConnection, String) -> Unit)? = null

    /** Resets registered hooks (useful for tests). */
    fun clear() {
        onLoginComplete = null
        onLoginFailed = null
    }
}
