package dev.sectile.network.protocol.player.enums

enum class ClientFeature(val id: Int) {
    SPLIT_VELOCITY(0),
    MANTLING(1),
    SPRINT_FORCE(2),
    CROUCH_SLIDE(3),
    SAFETY_ROLL(4),
    ;

    companion object {
        fun of(id: Int): ClientFeature? = entries.firstOrNull { it.id == id }
    }
}
