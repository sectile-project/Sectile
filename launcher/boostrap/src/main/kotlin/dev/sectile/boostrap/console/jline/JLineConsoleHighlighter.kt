package dev.sectile.boostrap.console.jline

import dev.sectile.engine.api.Engine
import org.jline.reader.Highlighter
import org.jline.reader.LineReader
import org.jline.utils.AttributedString
import org.jline.utils.AttributedStyle

internal class JLineConsoleHighlighter : Highlighter {
    private val unknownCommand: AttributedStyle = AttributedStyle.DEFAULT
        .foreground(AttributedStyle.RED)

    override fun highlight(reader: LineReader?, buffer: String): AttributedString {
        if (buffer.isBlank() || !Engine.isInitialized()) {
            return AttributedString(buffer)
        }

        val trimmed = buffer.trimStart()
        val spaceIdx = trimmed.indexOfFirst { it.isWhitespace() }
        val firstToken = (if (spaceIdx < 0) trimmed else trimmed.substring(0, spaceIdx)).removePrefix("/")
        if (firstToken.isNotEmpty() && Engine.commands.resolve(firstToken) == null) {
            return AttributedString(buffer, unknownCommand)
        }
        return AttributedString(buffer)
    }
}
