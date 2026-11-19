package dev.sectile.engine.command.tokenizer

/**
 * Splits a command line into whitespace-separated tokens while supporting quotes and escapes.
 *
 * Follows Hytale command tokenization rules:
 * - Double quotes (`"..."`) wrap multi-word tokens.
 * - Single quotes (`'...'`) open a quoted token only when appearing at token start (or right
 *   after `=` in `--arg='...'`), leaving mid-word apostrophes like `Let's` literal.
 * - Backslashes escape `"`, `'`, `\`, or whitespace outside/inside quotes.
 */
object CommandTokenizer {

    fun tokenize(commandLine: String): List<String> {
        val raw = commandLine.trim().let { trimmed ->
            if (trimmed.startsWith('/')) trimmed.substring(1).trimStart() else trimmed
        }
        if (raw.isEmpty()) {
            return emptyList()
        }

        val tokens = ArrayList<String>()
        val current = StringBuilder()

        var inToken = false
        var quote = '\u0000'

        var i = 0
        while (i < raw.length) {
            val c = raw[i]

            if (quote != '\u0000') {
                when (c) {
                    '\\' -> {
                        if (i + 1 >= raw.length) {
                            throw IllegalArgumentException("Invalid trailing escape character in command.")
                        }
                        val next = raw[i + 1]
                        if (next == '"' || next == '\'' || next == '\\') {
                            current.append(next)
                            i += 2
                            continue
                        }
                        throw IllegalArgumentException("Invalid escape sequence '\\$next'.")
                    }

                    quote -> {
                        quote = '\u0000'
                    }

                    else -> current.append(c)
                }
                i++
                continue
            }

            when {
                c.isWhitespace() -> {
                    if (inToken) {
                        tokens.add(current.toString())
                        current.setLength(0)
                        inToken = false
                    }
                }

                c == '\\' -> {
                    if (i + 1 >= raw.length) {
                        throw IllegalArgumentException("Invalid trailing escape character in command.")
                    }
                    val next = raw[i + 1]
                    if (next == '"' || next == '\'' || next == '\\' || next == ' ') {
                        if (!inToken) {
                            inToken = true
                        }
                        current.append(next)
                        i += 2
                        continue
                    }
                    throw IllegalArgumentException("Invalid escape sequence '\\$next'.")
                }

                c == '"' -> {
                    if (!inToken) {
                        inToken = true
                    }
                    quote = '"'
                }

                c == '\'' && (!inToken || (current.isNotEmpty() && current[current.length - 1] == '=')) -> {
                    if (!inToken) {
                        inToken = true
                    }
                    quote = '\''
                }

                else -> {
                    if (!inToken) {
                        inToken = true
                    }
                    current.append(c)
                }
            }
            i++
        }

        if (quote != '\u0000') {
            throw IllegalArgumentException("Unbalanced quotes in command input.")
        }

        if (inToken) {
            tokens.add(current.toString())
        }

        return tokens
    }
}
