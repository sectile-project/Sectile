package dev.sectile.engine.world

import dev.sectile.engine.api.player.Player
import dev.sectile.engine.api.world.World
import dev.sectile.engine.internal.InternalPlayer

class SimpleWorld(
    override val id: String,
    override val name: String,
) : World {

    override val players = LinkedHashSet<Player>()

    override fun add(player: Player) {
        if (players.add(player)) {
            if (player is InternalPlayer) {
                player.world = this
            }
        }
    }

    override fun remove(player: Player) {
        players.remove(player)
    }
}
