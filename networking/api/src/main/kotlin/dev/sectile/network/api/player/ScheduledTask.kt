package dev.sectile.network.api.player

/**
 * Handle for a scheduled task bound to a [dev.sectile.network.api.player.connection.PlayerConnection].
 */
fun interface ScheduledTask {

    /** Cancels this task if it has not already completed or been cancelled. */
    fun cancel()
}
