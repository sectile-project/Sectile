package dev.sectile.network.protocol.enums

/**
 * Client features that the server can enable or disable.
 *
 * Equivalent to `ClientFeature` of the Hytale protocol: the enum value is the byte that
 * travels on the wire.
 */
enum class ClientFeature(val id: Int) {
    SPLIT_VELOCITY(0),
    MANTLING(1),
    SPRINT_FORCE(2),
    CROUCH_SLIDE(3),
    SAFETY_ROLL(4),
    ;

    companion object {
        /** Returns the feature with that id, or `null` if it does not exist. */
        fun of(id: Int): ClientFeature? = entries.firstOrNull { it.id == id }
    }
}
