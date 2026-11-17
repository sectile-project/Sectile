package dev.sectile.kernel.config.section

import dev.sectile.kernel.config.ConfigSection

/**
 * An immutable empty configuration section.
 *
 * This singleton is intended as a shared empty value; attempts to modify [data] fail.
 */
object EmptyConfigSection : ConfigSection {
    override val name: String = ""
    override val data: Map<String, Any?> = mapOf()

    override fun <T> getList(key: String, clazz: Class<T>): List<T> = emptyList()
}
