package dev.sectile.network.api.player

import java.util.UUID

/**
 * Runtime session state for a connected player after login completes.
 *
 * Connection and session concerns only: game state such as position or health belongs to the
 * engine, not here.
 */
interface PlayerSessionState {

    /** Unique player identifier. */
    val uuid: UUID

    /** Player username. */
    val username: String

    /** Server-assigned entity network id. */
    val entityId: Int

    /** `true` once the client has reported ready for chunks or gameplay. */
    val ready: Boolean

    /** Client requested view radius in chunks. */
    val viewRadiusChunks: Int
}

/**
 * Mutable view of [PlayerSessionState] used by packet handlers to update player state.
 */
interface MutablePlayerSessionState : PlayerSessionState {
    override var ready: Boolean
    override var viewRadiusChunks: Int
}
