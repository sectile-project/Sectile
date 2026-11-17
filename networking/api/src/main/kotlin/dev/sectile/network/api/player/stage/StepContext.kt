package dev.sectile.network.api.player.stage

import dev.sectile.network.api.player.connection.PlayerConnection
import java.util.UUID

/**
 * Phase of the stage machine an extension is being notified about.
 */
enum class HookPhase {

    /** The step is about to run. */
    BEFORE,

    /** The step just finished. */
    AFTER,
}

/**
 * Read-only view of a connection inside the stage machine, handed to the extensions.
 *
 * It exposes everything a plugin needs to make a decision without touching the transport:
 * the connection itself, the point being notified and the identity resolved so far.
 */
interface StepContext {

    /** The connection the step belongs to. */
    val connection: PlayerConnection

    /** The point that is being notified. */
    val point: StepPoint

    /** Phase the extension is being notified about. */
    val phase: HookPhase

    /**
     * Identity of the player resolved so far, or `null` when it is not known yet.
     *
     * It is filled in as the login progresses: after the identity token is validated the
     * subject and the username are already there.
     */
    val login: LoginSnapshot?

    /**
     * Resumes a pipeline that was paused with [StepDecision.Hold].
     *
     * It must be called asynchronously (from another thread or a later callback): calling it
     * before the hook returns has no effect.
     *
     * @param decision the final decision.
     */
    fun resume(decision: StepDecision)
}

/**
 * Read-only snapshot of the identity resolved during the login.
 */
interface LoginSnapshot {

    /** UUID of the player, or `null` when it is not known yet. */
    val uuid: UUID?

    /** Username of the player, or `null` when it is not known yet. */
    val username: String?

    /** Subject of the identity token, or `null` when it is not known yet. */
    val identitySubject: String?

    /** Identity token sent by the client, or `null` in insecure mode. */
    val identityToken: String?

    /** Access token validated for the session, or `null` in insecure mode. */
    val accessToken: String?

    /** `true` when the server runs in authenticated mode. */
    val authenticated: Boolean
}
