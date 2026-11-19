package dev.sectile.engine.chat.format

import dev.sectile.engine.chat.color.Color
import dev.sectile.engine.chat.color.Color.Companion.AQUA
import dev.sectile.engine.chat.color.Color.Companion.BLACK
import dev.sectile.engine.chat.color.Color.Companion.BLUE
import dev.sectile.engine.chat.color.Color.Companion.DARK_AQUA
import dev.sectile.engine.chat.color.Color.Companion.DARK_BLUE
import dev.sectile.engine.chat.color.Color.Companion.DARK_GRAY
import dev.sectile.engine.chat.color.Color.Companion.DARK_GREEN
import dev.sectile.engine.chat.color.Color.Companion.DARK_PURPLE
import dev.sectile.engine.chat.color.Color.Companion.DARK_RED
import dev.sectile.engine.chat.color.Color.Companion.GOLD
import dev.sectile.engine.chat.color.Color.Companion.GRAY
import dev.sectile.engine.chat.color.Color.Companion.GREEN
import dev.sectile.engine.chat.color.Color.Companion.LIGHT_PURPLE
import dev.sectile.engine.chat.color.Color.Companion.RED
import dev.sectile.engine.chat.color.Color.Companion.WHITE
import dev.sectile.engine.chat.color.Color.Companion.YELLOW
import dev.sectile.engine.chat.component.Message

/**
 * Transforms legacy-formatted text (e.g. `&aHello &lWorld!` or `&#FF5500Hex`) into [Message]s.
 *
 * Unlike vanilla 1.8 where a color code after style codes clears those styles immediately
 * (`&bTest&l&aOne` losing bold), styles applied right before a color code carry over to the
 * next text fragment (`&bTest&n&k&l&aOne` == `&bTest&a&l&k&nOne`).
 */
object ChatLegacy {

    const val COLOR_CHAR: Char = '§'

    fun color(textWithAmpersand: String): String = textWithAmpersand.replace('&', COLOR_CHAR)

    fun format(legacyText: String, specialChar: Char = '&'): Message {
        val parts = formatToList(legacyText, specialChar)
        return when (parts.size) {
            0 -> Message.of("")

            1 -> parts[0]

            else -> Message().apply {
                for (part in parts) {
                    append(part)
                }
            }
        }
    }

    fun formatColorChar(legacyText: String): Message = format(legacyText, COLOR_CHAR)

    fun formatToList(legacyText: String, specialChar: Char = '&'): List<Message> {
        val components = ArrayList<Message>()
        val length = legacyText.length
        val builder = StringBuilder()

        var color: Color? = null
        var bold = false
        var italic = false
        var underline = false
        var strikethrough = false

        fun flush() {
            if (builder.isEmpty()) return
            val segment = Message(rawText = builder.toString()).apply {
                color?.let { this.color = it }
                if (bold) this.bold = true
                if (italic) this.italic = true
                if (underline) this.underline = true
                if (strikethrough) this.strikethrough = true
            }
            components.add(segment)
            builder.setLength(0)
        }

        fun resetStyles() {
            bold = false
            italic = false
            underline = false
            strikethrough = false
        }

        fun applyColor(nextColor: Color) {
            if (color == nextColor) return
            if (builder.isNotEmpty()) {
                flush()
                resetStyles()
            }
            color = nextColor
        }

        var i = 0
        while (i < length) {
            val ch = legacyText[i]
            if (ch == '\n') {
                flush()
                components.add(Message(rawText = "\n"))
                i++
                continue
            }

            if (ch != specialChar) {
                builder.append(ch)
                i++
                continue
            }

            if (i + 1 >= length) {
                builder.append(ch)
                break
            }

            val code = legacyText[i + 1]

            // Support inline hex colors like &#FF5500
            if (code == '#' && i + 7 < length) {
                val hex = parseHex(legacyText, i + 2)
                if (hex != -1) {
                    applyColor(Color.hex(hex))
                    i += 8
                    continue
                }
            }

            val legacyColor = byLegacyCode(code)
            if (legacyColor != null) {
                applyColor(legacyColor)
                i += 2
                continue
            }

            when (code.lowercaseChar()) {
                'l' -> {
                    flush()
                    bold = true
                    i += 2
                }

                'o' -> {
                    flush()
                    italic = true
                    i += 2
                }

                'n' -> {
                    flush()
                    underline = true
                    i += 2
                }

                'm' -> {
                    flush()
                    strikethrough = true
                    i += 2
                }

                // Obfuscated is not supported by Hytale formatted messages; consume the code cleanly.
                'k' -> {
                    i += 2
                }

                'r' -> {
                    flush()
                    color = null
                    resetStyles()
                    i += 2
                }

                else -> {
                    builder.append(ch)
                    i++
                }
            }
        }

        flush()
        return components
    }

    private fun parseHex(text: String, start: Int): Int {
        var value = 0
        for (offset in 0 until 6) {
            val digit = text[start + offset].digitToIntOrNull(16) ?: return -1
            value = (value shl 4) or digit
        }
        return value
    }

    fun byLegacyCode(code: Char): Color? = when (code) {
        '0' -> BLACK
        '1' -> DARK_BLUE
        '2' -> DARK_GREEN
        '3' -> DARK_AQUA
        '4' -> DARK_RED
        '5' -> DARK_PURPLE
        '6' -> GOLD
        '7' -> GRAY
        '8' -> DARK_GRAY
        '9' -> BLUE
        'a' -> GREEN
        'b' -> AQUA
        'c' -> RED
        'd' -> LIGHT_PURPLE
        'e' -> YELLOW
        'f' -> WHITE
        else -> null
    }
}
