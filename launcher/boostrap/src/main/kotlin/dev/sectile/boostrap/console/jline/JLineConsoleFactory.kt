package dev.sectile.boostrap.console.jline

import dev.sectile.kernel.Kernel
import org.jline.reader.LineReaderBuilder
import org.jline.terminal.Terminal
import org.jline.terminal.TerminalBuilder
import java.io.IOException

fun createJLineConsole(): JLineConsole? {
    val terminal: Terminal
    try {
        terminal = TerminalBuilder.builder()
            .system(true)
            .build()
    } catch (e: IOException) {
        System.err.println("Error on creating terminal")
        e.printStackTrace(System.err)
        return null
    }

    val reader = LineReaderBuilder.builder()
        .terminal(terminal)
        .highlighter(JLineConsoleHighlighter())
        .completer(JLineConsoleCompleter())
        .build()

    val console = JLineConsole(terminal, reader)
    Kernel.addStopTask { console.stop() }

    return console
}
