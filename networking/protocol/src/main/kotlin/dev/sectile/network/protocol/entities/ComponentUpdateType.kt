package dev.sectile.network.protocol.entities

/**
 * Component types that can be updated on an entity.
 *
 * Equivalent to `ComponentUpdateType` of the Hytale protocol: the enum value is the id
 * that travels ahead of the component data.
 */
enum class ComponentUpdateType(val id: Int) {
    NAMEPLATE(0),
    UI_COMPONENTS(1),
    COMBAT_TEXT(2),
    MODEL(3),
    PLAYER_SKIN(4),
    ITEM(5),
    BLOCK(6),
    EQUIPMENT(7),
    ENTITY_STATS(8),
    TRANSFORM(9),
    MOVEMENT_STATES(10),
    ENTITY_EFFECTS(11),
    INTERACTIONS(12),
    DYNAMIC_LIGHT(13),
    INTERACTABLE(14),
    INTANGIBLE(15),
    INVULNERABLE(16),
    RESPOND_TO_HIT(17),
    HITBOX_COLLISION(18),
    REPULSION(19),
    PREDICTION(20),
    AUDIO(21),
    MOUNTED(22),
    NEW_SPAWN(23),
    ACTIVE_ANIMATIONS(24),
    PROP(25),
    CARRIED_BLOCK(26),
    PREVENT_INVENTORY_ACCESS(27),
    PREFAB_PREVIEW(28),
    PREVENT_EMOTES(29),
    BEAMS(30),
    PREVENT_DEBUG_SCREENS(31),
    ;

    companion object {
        /** Returns the type with that id, or `null` if it does not exist. */
        fun of(id: Int): ComponentUpdateType? = entries.firstOrNull { it.id == id }
    }
}
