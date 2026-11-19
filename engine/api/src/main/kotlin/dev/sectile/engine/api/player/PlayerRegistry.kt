package dev.sectile.engine.api.player

import dev.sectile.network.api.player.connection.PlayerConnection
import java.util.UUID

interface PlayerRegistry {
    fun all(): Collection<Player>

    fun forEach(action: (Player) -> Unit)

    fun byUuid(uuid: UUID): Player?

    fun byName(name: String): Player?

    fun byConnection(connection: PlayerConnection): Player?

    fun byNameExact(name: String): Player?
}
