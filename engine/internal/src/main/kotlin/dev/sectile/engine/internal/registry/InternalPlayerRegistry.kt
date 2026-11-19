package dev.sectile.engine.internal.registry

import dev.sectile.engine.api.player.Player
import dev.sectile.engine.api.player.PlayerRegistry
import dev.sectile.network.api.player.connection.PlayerConnection
import java.util.*
import java.util.Locale.getDefault
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

object InternalPlayerRegistry : PlayerRegistry {
    private val PLAYERS: MutableCollection<Player> = CopyOnWriteArrayList()
    private val BY_UUID = ConcurrentHashMap<UUID, Player>()

    fun register(player: Player) {
        player.connection
        PLAYERS.add(player)
        BY_UUID[player.uuid] = player
    }

    fun unregister(player: Player) {
        PLAYERS.remove(player)
        BY_UUID.remove(player.uuid)
    }

    override fun all(): Collection<Player> = BY_UUID.values

    override fun forEach(action: (Player) -> Unit) {
        PLAYERS.forEach { action(it) }
    }

    override fun byUuid(uuid: UUID): Player? = BY_UUID[uuid]

    override fun byName(name: String): Player? {
        var found: Player? = null
        var delta = Int.MAX_VALUE
        val lowerName = name.lowercase(getDefault())

        for (player in PLAYERS) {
            if (player.name.lowercase(getDefault()).startsWith(lowerName)) {
                val curDelta = player.name.length - lowerName.length
                if (curDelta < delta) {
                    found = player
                    delta = curDelta
                }
                if (curDelta == 0) break
            }
        }
        return found
    }

    override fun byConnection(connection: PlayerConnection): Player? = BY_UUID[connection.session.uuid]

    override fun byNameExact(name: String): Player? = PLAYERS.find { it.name == name }
}
