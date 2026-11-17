package dev.sectile.microkernel.plugin.manifest

import java.io.InputStream

object CustomPropertiesParser {

    /**
     * Parses the `key=value` lines of a plugin manifest.
     *
     * Blank lines and comments starting with `#` or `!` are ignored. Keys and values are
     * trimmed, and both LF and CRLF line endings are accepted.
     */
    fun parse(inputStream: InputStream): Map<String, String> {
        val properties = LinkedHashMap<String, String>()

        inputStream.bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.forEach { line ->
                val content = line.trim()
                if (content.isEmpty() || content.startsWith("#") || content.startsWith("!")) {
                    return@forEach
                }

                val separator = content.indexOf('=')
                if (separator < 0) {
                    throw IllegalArgumentException("Missing '=' in line: $content")
                }

                val key = content.substring(0, separator).trim()
                if (key.isNotEmpty()) {
                    properties[key] = content.substring(separator + 1).trim()
                }
            }
        }

        return properties
    }
}
