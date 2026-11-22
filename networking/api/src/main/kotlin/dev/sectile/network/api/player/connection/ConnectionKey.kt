package dev.sectile.network.api.player.connection

/**
 * Typed attribute key for per-connection state stored on a [PlayerConnection].
 */
class ConnectionKey<T : Any>(val name: String) {

    override fun equals(other: Any?): Boolean = this === other || (other is ConnectionKey<*> && name == other.name)

    override fun hashCode(): Int = name.hashCode()

    override fun toString(): String = "ConnectionKey($name)"
}
