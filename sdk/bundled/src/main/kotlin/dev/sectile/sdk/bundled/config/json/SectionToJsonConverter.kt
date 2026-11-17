package dev.sectile.sdk.bundled.config.json

import dev.sectile.kernel.config.ConfigSection

class SectionToJsonConverter(private val indentSpaces: Int) {
    private val builder = StringBuilder()
    private var indentLevel = 0

    fun toJson(section: ConfigSection): String {
        builder.setLength(0)
        indentLevel = 0
        appendMap(section.data)
        return builder.toString()
    }

    private fun appendMap(map: Map<*, *>) {
        if (map.isEmpty()) {
            builder.append("{}")
            return
        }

        builder.append("{\n")
        indentLevel++

        map.entries.forEachIndexed { index, entry ->
            appendIndent()
            appendString(entry.key.toString())
            builder.append(": ")

            appendObject(entry.value)

            if (index < map.size - 1) builder.append(",")
            builder.append("\n")
        }

        indentLevel--
        appendIndent()
        builder.append("}")
    }

    private fun appendObject(value: Any?) {
        when (value) {
            null -> builder.append("null")

            is Boolean -> builder.append(value)

            is Number -> {
                // JSON not support NaN or Infinity
                val invalid = (value is Double && (value.isNaN() || value.isInfinite())) ||
                    (value is Float && (value.isNaN() || value.isInfinite()))
                builder.append(if (invalid) "null" else value.toString())
            }

            is ConfigSection -> appendMap(value.data)

            is Map<*, *> -> appendMap(value)

            is Collection<*> -> appendCollection(value)

            is Array<*> -> appendCollection(value.asList())

            else -> appendString(value.toString())
        }
    }

    private fun appendCollection(collection: Collection<*>) {
        if (collection.isEmpty()) {
            builder.append("[]")
            return
        }

        builder.append("[\n")
        indentLevel++

        collection.forEachIndexed { index, item ->
            appendIndent()
            appendObject(item)

            if (index < collection.size - 1) builder.append(",")
            builder.append("\n")
        }

        indentLevel--
        appendIndent()
        builder.append("]")
    }

    private fun appendString(value: String) {
        builder.append('"')
        for (c in value) {
            when (c) {
                '"' -> builder.append("\\\"")

                '\\' -> builder.append("\\\\")

                '\n' -> builder.append("\\n")

                '\r' -> builder.append("\\r")

                '\t' -> builder.append("\\t")

                '\b' -> builder.append("\\b")

                '\u000C' -> builder.append("\\f")

                else -> if (c < ' ') {
                    builder.append("\\u").append(c.code.toString(16).padStart(4, '0'))
                } else {
                    builder.append(c)
                }
            }
        }
        builder.append('"')
    }

    private fun appendIndent() {
        builder.append(" ".repeat(indentLevel * indentSpaces))
    }
}
