package dev.sectile.network.api.player

import java.util.UUID

/**
 * Runtime session state for a connected player after login completes.
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

    /** World X coordinate. */
    val positionX: Double

    /** World Y coordinate. */
    val positionY: Double

    /** World Z coordinate. */
    val positionZ: Double

    /** Player yaw orientation in radians. */
    val yaw: Float

    /** Player pitch orientation in radians. */
    val pitch: Float
}

/**
 * Mutable view of [PlayerSessionState] used by packet handlers to update player state.
 */
interface MutablePlayerSessionState : PlayerSessionState {
    override var ready: Boolean
    override var viewRadiusChunks: Int
    override var positionX: Double
    override var positionY: Double
    override var positionZ: Double
    override var yaw: Float
    override var pitch: Float
}
