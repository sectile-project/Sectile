package dev.sectile.boostrap.console.jline

import dev.sectile.engine.api.Engine
import dev.sectile.engine.api.command.ConsoleCommandSender
import org.jline.reader.Candidate
import org.jline.reader.Completer
import org.jline.reader.LineReader
import org.jline.reader.ParsedLine

internal class JLineConsoleCompleter(
    private val sender: ConsoleCommandSender,
) : Completer {
    override fun complete(reader: LineReader?, line: ParsedLine, candidates: MutableList<Candidate?>) {
        if (!Engine.isInitialized()) return

        val wordIndex = line.wordIndex()
        if (wordIndex < 0) return

        val word = line.word()
        val cursor = line.wordCursor()
        val prefix = if (cursor in 0..word.length) word.substring(0, cursor) else word

        val words = ArrayList(line.words())
        while (words.size <= wordIndex) {
            words.add("")
        }
        words[wordIndex] = prefix

        for (suggestion in Engine.commands.suggestTokens(sender, words, wordIndex).sorted()) {
            candidates.add(Candidate(suggestion))
        }
    }
}
