package dev.sectile.network.protocol.enums

/**
 * Armor visibility option.
 *
 * Equivalent to `ArmorVisibilityOption` of the Hytale protocol.
 */
enum class ArmorVisibilityOption(val id: Int) {
    ALL(0),
    HELMET_ONLY(1),
    NONE(2),
    ;

    companion object {
        /** Returns the option with that id, or `null` if it does not exist. */
        fun of(id: Int): ArmorVisibilityOption? = entries.firstOrNull { it.id == id }
    }
}
