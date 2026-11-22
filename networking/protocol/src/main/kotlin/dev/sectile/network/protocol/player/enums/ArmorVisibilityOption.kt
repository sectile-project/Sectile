package dev.sectile.network.protocol.player.enums

enum class ArmorVisibilityOption(val id: Int) {
    ALL(0),
    HELMET_ONLY(1),
    NONE(2),
    ;

    companion object {
        fun of(id: Int): ArmorVisibilityOption? = entries.firstOrNull { it.id == id }
    }
}
