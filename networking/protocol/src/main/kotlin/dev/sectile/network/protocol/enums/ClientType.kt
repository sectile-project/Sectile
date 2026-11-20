package dev.sectile.network.protocol.enums

/**
 * Type of client that connects. Equivalent to `ClientType` of the Hytale protocol.
 */
enum class ClientType {
    GAME,
    EDITOR,
    ;

    companion object {
        fun of(ordinal: Int): ClientType? = entries.getOrNull(ordinal)
    }
}
