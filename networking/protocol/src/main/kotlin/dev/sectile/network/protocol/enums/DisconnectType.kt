package dev.sectile.network.protocol.enums

/**
 * Disconnection type. Equivalent to `DisconnectType` of the Hytale protocol.
 */
enum class DisconnectType {
    DISCONNECT,
    CRASH,
    ;

    companion object {
        fun of(ordinal: Int): DisconnectType? = entries.getOrNull(ordinal)
    }
}
