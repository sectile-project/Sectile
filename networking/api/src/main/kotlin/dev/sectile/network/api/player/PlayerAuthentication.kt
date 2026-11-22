package dev.sectile.network.api.player

import java.util.UUID

/**
 * Result of player authentication during login.
 *
 * Populated once login completes.
 */
interface PlayerAuthentication {

    /** Unique player identifier. */
    val uuid: UUID

    /** Player username. */
    val username: String

    /** Identity token sent by the client in `Connect` (`null` in insecure mode). */
    val identityToken: String?

    /** Access token validated for this session (`null` in insecure mode). */
    val accessToken: String?

    /** `true` if full cryptographic authentication was completed. */
    val authenticated: Boolean
}

/**
 * Default immutable implementation of [PlayerAuthentication].
 */
data class SimplePlayerAuthentication(
    override val uuid: UUID,
    override val username: String,
    override val identityToken: String? = null,
    override val accessToken: String? = null,
    override val authenticated: Boolean = true,
) : PlayerAuthentication
