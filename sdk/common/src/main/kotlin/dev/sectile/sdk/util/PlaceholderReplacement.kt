package dev.sectile.sdk.util

/**
 * Utility class for placeholder replacement in strings
 */
object PlaceholderReplacement {

    @JvmStatic
    fun getReplacements(vararg replacements: Any): Map<String, String> {
        val pairCount = replacements.size / 2
        if (pairCount == 0) {
            return emptyMap()
        }
        val map = HashMap<String, String>((pairCount / 0.75f).toInt() + 1)
        for (i in 0 until replacements.size - 1 step 2) {
            map[replacements[i].toString()] = replacements[i + 1].toString()
        }
        return map
    }

    @JvmStatic
    fun apply(message: String, vararg replacements: Any): String {
        if (replacements.size < 2 || message.isEmpty()) {
            return message
        }

        val firstPercent = message.indexOf('%')
        if (firstPercent == -1) {
            return message
        }

        if (replacements.size > 16) {
            return applyFromFirstPercent(message, firstPercent, getReplacements(*replacements))
        }

        val length = message.length
        var result: StringBuilder? = null
        var lastAppendIndex = 0
        var i = firstPercent

        while (i < length) {
            val end = message.indexOf('%', i + 1)
            if (end == -1) {
                break
            }

            val key = message.substring(i + 1, end)
            val replacement = findVarargReplacement(key, replacements)

            if (replacement != null) {
                val builder = result ?: StringBuilder(length).also { result = it }
                builder.append(message, lastAppendIndex, i)
                builder.append(replacement)
                lastAppendIndex = end + 1
                i = message.indexOf('%', lastAppendIndex)
                if (i == -1) {
                    break
                }
            } else {
                i = end
            }
        }

        val builder = result ?: return message
        if (lastAppendIndex < length) {
            builder.append(message, lastAppendIndex, length)
        }

        return builder.toString()
    }

    @JvmStatic
    fun apply(message: String, replacements: Map<String, String>): String {
        if (replacements.isEmpty() || message.isEmpty()) {
            return message
        }

        val firstPercent = message.indexOf('%')
        if (firstPercent == -1) {
            return message
        }

        return applyFromFirstPercent(message, firstPercent, replacements)
    }

    private fun applyFromFirstPercent(
        message: String,
        firstPercent: Int,
        replacements: Map<String, String>,
    ): String {
        val length = message.length
        var result: StringBuilder? = null
        var lastAppendIndex = 0
        var i = firstPercent

        while (i < length) {
            val end = message.indexOf('%', i + 1)
            if (end == -1) {
                break
            }

            val key = message.substring(i + 1, end)
            val replacement = replacements[key]

            if (replacement != null) {
                val builder = result ?: StringBuilder(length).also { result = it }
                builder.append(message, lastAppendIndex, i)
                builder.append(replacement)
                lastAppendIndex = end + 1
                i = message.indexOf('%', lastAppendIndex)
                if (i == -1) {
                    break
                }
            } else {
                i = end
            }
        }

        val builder = result ?: return message
        if (lastAppendIndex < length) {
            builder.append(message, lastAppendIndex, length)
        }

        return builder.toString()
    }

    private fun findVarargReplacement(key: String, replacements: Array<out Any>): String? {
        var matchValue: Any? = null
        for (i in 0 until replacements.size - 1 step 2) {
            if (replacements[i].toString() == key) {
                matchValue = replacements[i + 1]
            }
        }
        return matchValue?.toString()
    }
}
