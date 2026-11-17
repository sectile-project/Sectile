package dev.sectile.boostrap.console.jline

import org.jline.reader.Candidate
import org.jline.reader.Completer
import org.jline.reader.LineReader
import org.jline.reader.ParsedLine

internal class JLineConsoleCompleter : Completer {
    override fun complete(reader: LineReader?, line: ParsedLine, candidates: MutableList<Candidate?>) {
        if (line.line().indexOf(' ') == -1) {
            // TODO: Command complete
            candidates.add(Candidate("stop"))
        }
    }
}
