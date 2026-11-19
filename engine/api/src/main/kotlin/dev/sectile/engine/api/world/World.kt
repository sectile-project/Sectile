package dev.sectile.engine.api.world

import dev.sectile.engine.api.player.Player

/**
 * A world that owns its players and their positions.
 *
 * The world is the single source of truth for membership and position. Moving a player is one
 * [move] call so both worlds and the player stay consistent without an external registry.
 */
interface World {

    /** Stable identifier of the world. */
    val id: String

    /** Display name of the world. */
    val name: String

    /** Players currently in this world. */
    val players: Set<Player>

    /** Adds [player] and initializes its position at the origin. */
    fun add(player: Player)

    /** Removes [player] from this world and drops its stored position. */
    fun remove(player: Player)

    /** Moves [player] to [target], updating both worlds and [Player.world]. */
    fun move(
        player: Player,
        target: World,
    ) {
        remove(player)
        target.add(player)
    }
}
