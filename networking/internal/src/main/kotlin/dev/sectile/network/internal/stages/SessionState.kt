package dev.sectile.network.internal.stages

import dev.sectile.network.api.player.ScheduledTask
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Mutable state shared by the session stages (`PRE_LOGIN` and `POST_LOGIN`).
 */
internal class SessionState {

    /** Phase of the world setup. */
    var phase: Phase = Phase.ASSETS

    /** `true` once the session has been started. */
    val started: AtomicBoolean = AtomicBoolean(false)

    /** Identifier of the next ping sent to the client. */
    val pingId: AtomicInteger = AtomicInteger(0)

    /** Counter of the network ticks sent to the client. */
    val serverTick: AtomicInteger = AtomicInteger(0)

    /** `true` once the tick and ping tasks are running. */
    var tasksStarted: Boolean = false

    /** Task that sends the network ticks. */
    var tickTask: ScheduledTask? = null

    /** Task that sends the pings. */
    var pingTask: ScheduledTask? = null

    /**
     * Phase of the world setup.
     */
    enum class Phase {

        /** The client is downloading the assets it needs. */
        ASSETS,

        /** The assets are loaded: the client is finishing the setup. */
        LOADING,

        /** The client is playing. */
        PLAY,
    }
}
