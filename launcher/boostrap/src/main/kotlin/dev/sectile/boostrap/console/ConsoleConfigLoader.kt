package dev.sectile.boostrap.console

import dev.sectile.boostrap.terminal.config.TerminalColors
import dev.sectile.boostrap.terminal.config.TerminalConfig
import dev.sectile.engine.chat.color.Color
import dev.sectile.kernel.config.ConfigService
import java.util.Locale

fun loadConsoleConfig(configService: ConfigService): TerminalConfig? {
    val section = configService.createIfAbsentAndLoad("console.yml")
    if (!section.getBoolean("interactive", true)) {
        return null
    }

    val defaults = TerminalConfig()
    val defaultColors = TerminalColors()

    val autocomplete = section.getSection("autocomplete")
    val menu = section.getSection("menu")
    val colorsSection = section.getSection("colors")

    return TerminalConfig().apply {
        defaultPrompt = section.getString("prompt", defaults.defaultPrompt)
        autoCompleteWhileTyping = autocomplete.getBoolean("while-typing", defaults.autoCompleteWhileTyping)
        tabCompletion = autocomplete.getBoolean("tab-completion", defaults.tabCompletion)
        inlineGhostSuggestion = autocomplete.getBoolean("inline-ghost", defaults.inlineGhostSuggestion)
        acceptWithRightArrow = autocomplete.getBoolean("accept-with-right-arrow", defaults.acceptWithRightArrow)
        appendSpaceOnAccept = autocomplete.getBoolean("append-space-on-accept", defaults.appendSpaceOnAccept)
        multiColumnMenu = menu.getBoolean("multi-column", defaults.multiColumnMenu)
        maxMenuRows = menu.getInt("max-rows", defaults.maxMenuRows).coerceAtLeast(1)
        colors = TerminalColors().apply {
            prompt = parseTerminalColor(colorsSection["prompt"], defaultColors.prompt, allowNone = true)
            ghostText = parseTerminalColor(colorsSection["ghost-text"], defaultColors.ghostText)
            menuItem = parseTerminalColor(colorsSection["menu-item"], defaultColors.menuItem)
            selectedItem = parseTerminalColor(colorsSection["selected-item"], defaultColors.selectedItem)
            scrollHint = parseTerminalColor(colorsSection["scroll-hint"], defaultColors.scrollHint)
            boldSelectedItem = colorsSection.getBoolean("bold-selected-item", defaultColors.boldSelectedItem)
        }
    }
}

internal fun parseTerminalColor(
    raw: Any?,
    defaultRgb: Int,
    allowNone: Boolean = false,
): Int {
    if (raw == null) return defaultRgb
    if (raw is Number) {
        val value = raw.toInt()
        if (allowNone && value == TerminalColors.NONE) return TerminalColors.NONE
        return Color.hex(value).rgb
    }

    val text = raw.toString().trim()
    if (text.isEmpty() || text.equals("none", ignoreCase = true) || text.equals("default", ignoreCase = true)) {
        return if (allowNone) TerminalColors.NONE else defaultRgb
    }

    Color.byName(text.lowercase(Locale.ROOT).replace('-', '_').replace(' ', '_'))?.let { return it.rgb }

    val hex = when {
        text.startsWith("0x", ignoreCase = true) -> text.substring(2)
        text.startsWith("#") -> text.substring(1)
        else -> text
    }

    if ((hex.length == 3 || hex.length == 6) && hex.all { it.isHexDigit() }) {
        return Color.hex(hex).rgb
    }

    return defaultRgb
}

private fun Char.isHexDigit(): Boolean = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'
