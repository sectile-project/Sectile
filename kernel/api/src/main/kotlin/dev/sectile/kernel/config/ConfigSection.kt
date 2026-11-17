package dev.sectile.kernel.config

import dev.sectile.kernel.config.section.EmptyConfigSection
import dev.sectile.kernel.config.section.MapConfigSection
import kotlin.time.Duration

/**
 * Represents a named configuration section backed by a mutable key-value map.
 *
 * <p>A section may contain primitive values, collections, or nested sections represented
 * internally as nested maps.</p>
 */
interface ConfigSection {
    val name: String
    val data: Map<String, Any?>

    operator fun get(key: String): Any? = data[key]
    operator fun set(key: String, value: Any?) {
        if (isReadOnly()) {
            throw UnsupportedOperationException("ConfigSection is not mutable")
        }
        (data as MutableMap<String, Any?>)[key] = value
    }

    infix fun String.to(value: Any?) {
        set(this, value)
    }

    fun isReadOnly(): Boolean = data !is MutableMap<*, *>

    fun getString(key: String): String? = get(key)?.toString()
    fun getString(key: String, default: String): String = getString(key) ?: default
    fun getNumber(key: String, default: Number = 0): Number? = get(key) as? Number ?: default
    fun getInt(key: String): Int = (get(key) as? Number)?.toInt() ?: 0
    fun getInt(key: String, default: Int): Int = (get(key) as? Number)?.toInt() ?: default
    fun getLong(key: String): Long = (get(key) as? Number)?.toLong() ?: 0L
    fun getLong(key: String, default: Long): Long = (get(key) as? Number)?.toLong() ?: default
    fun getDouble(key: String): Double = (get(key) as? Number)?.toDouble() ?: 0.0
    fun getDouble(key: String, default: Double): Double = (get(key) as? Number)?.toDouble() ?: default
    fun getBoolean(key: String): Boolean = get(key) as? Boolean ?: false
    fun getBoolean(key: String, default: Boolean): Boolean = get(key) as? Boolean ?: default

    fun <T> getList(key: String, clazz: Class<T>): List<T>
    fun getStringList(key: String): List<String> = getList(key, String::class.java)

    fun <T : Enum<T>> getEnum(key: String, enumClass: Class<T>): T? {
        var value = get(key)

        if (value?.javaClass == enumClass) {
            return value as T
        }

        value = value.toString()
        if (value.isBlank()) {
            return null
        }

        value = value.uppercase().replace(" ", "_")
        return enumClass.enumConstants.firstOrNull { it.name == value }
    }

    fun <T : Enum<T>> getEnum(key: String, default: T): T = getEnum(key, default.javaClass) ?: default

    /**
     * Returns the nested section stored at the given key, or EmptyConfigSection if the value
     * does not exist or is not backed by a mutable map.
     *
     * @param key the nested section key
     * @return the nested section, or EmptyConfigSection if no compatible section exists
     */
    @Suppress("UNCHECKED_CAST")
    fun getSection(key: String): ConfigSection = when (val value = data[key]) {
        is ConfigSection -> value

        is MutableMap<*, *> -> MapConfigSection(value as MutableMap<String, Any?>, key)

        is Map<*, *> -> {
            val mutableMap = mutableMapOf<String, Any?>()
            value.forEach { (k, v) -> mutableMap[k.toString()] = v }
            MapConfigSection(mutableMap, key)
        }

        else -> EmptyConfigSection
    }

    fun getDuration(key: String, default: Duration): Duration {
        val obj = get(key)
        if (obj is Duration) {
            return obj
        }
        if (obj is String) {
            return Duration.parseOrNull(obj) ?: default
        }
        return default
    }

    /**
     * Returns the nested section stored at the given key, creating a new empty section
     * if it does not already exist.
     *
     * <p>If a compatible section is already present, it is returned as-is.</p>
     *
     * @param key the nested section key
     * @return an existing or newly created nested section
     */
    fun getOrCreateSection(key: String): ConfigSection {
        val existing = getSection(key)
        if (existing != EmptyConfigSection) {
            return existing
        }

        val newMap = LinkedHashMap<String, Any?>()
        set(key, newMap)
        return MapConfigSection(newMap, key)
    }
}
