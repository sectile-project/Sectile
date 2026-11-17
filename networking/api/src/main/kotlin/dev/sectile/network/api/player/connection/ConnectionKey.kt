package dev.sectile.network.api.player.connection

/**
 * Typed attribute key for per-connection state stored on a [PlayerConnection].
 */
class ConnectionKey<T : Any>(val name: String) {
    override fun toString(): String = "ConnectionKey($name)"
}
