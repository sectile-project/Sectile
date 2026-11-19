package dev.sectile.engine.api.command.argument

import dev.sectile.engine.api.Engine
import dev.sectile.engine.api.player.Player
import dev.sectile.engine.api.world.World
import java.util.Locale
import java.util.UUID

class Arguments(
    val tokens: List<String>,
    private val flags: Set<String> = emptySet(),
) {

    var index: Int = 0
        private set

    val size: Int
        get() = tokens.size

    val isEmpty: Boolean
        get() = tokens.isEmpty()

    val isNotEmpty: Boolean
        get() = tokens.isNotEmpty()

    fun hasNext(): Boolean = index < tokens.size

    fun remaining(): Int = (tokens.size - index).coerceAtLeast(0)

    fun isValidIndex(idx: Int): Boolean = idx in tokens.indices

    fun isInvalidIndex(idx: Int): Boolean = idx !in tokens.indices

    fun exists(idx: Int): Boolean = idx in tokens.indices

    fun hasFlag(name: String, vararg aliases: String): Boolean = normalizeKey(name) in flags || aliases.any { normalizeKey(it) in flags }

    fun move(count: Int = 1) {
        index = (index + count).coerceIn(0, tokens.size)
    }

    fun seek(position: Int) {
        index = position.coerceIn(0, tokens.size)
    }

    fun reset() {
        index = 0
    }

    operator fun get(idx: Int): String? = tokens.getOrNull(idx)

    fun nextString(): String? = if (index < tokens.size) tokens[index++] else null

    fun nextBoolean(default: Boolean = false): Boolean = nextString()?.let(::parseBooleanToken) ?: default

    fun nextInt(): Int? = nextString()?.toIntOrNull()

    fun nextInt(default: Int): Int = nextInt() ?: default

    fun nextPositiveInt(): Int? = nextInt()?.takeIf { it > 0 }

    fun nextDouble(): Double? = nextString()?.toDoubleOrNull()?.takeIf(Double::isFinite)

    fun nextDouble(default: Double): Double = nextDouble() ?: default

    fun nextUuid(): UUID? = nextString()?.let { runCatching { UUID.fromString(it) }.getOrNull() }

    fun nextPlayer(): Player? = nextString()?.let(Engine.players::byName)

    fun nextWorld(): World? = nextString()?.let(Engine.worlds::byId)

    fun <E : Enum<E>> nextEnum(enumClass: Class<E>): E? = nextString()?.let { findEnum(enumClass, it) }

    inline fun <reified E : Enum<E>> nextEnum(): E? = nextEnum(E::class.java)

    private fun parseBooleanToken(raw: String): Boolean? = when (raw.lowercase(Locale.ROOT)) {
        "true", "yes", "on", "1" -> true
        "false", "no", "off", "0" -> false
        else -> null
    }

    private fun <E : Enum<E>> findEnum(enumClass: Class<E>, raw: String): E? {
        val constants = enumClass.enumConstants ?: return null
        return constants.firstOrNull { it.name.equals(raw, ignoreCase = true) }
    }

    private fun normalizeKey(raw: String): String = raw.removePrefix("--").removePrefix("-").lowercase(Locale.ROOT)
}
