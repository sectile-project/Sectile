package dev.sectile.boostrap.console.jline

import dev.sectile.engine.api.command.ConsoleCommandSender
import dev.sectile.kernel.Kernel
import org.jline.reader.LineReader
import org.jline.reader.LineReaderBuilder
import org.jline.terminal.Terminal
import org.jline.terminal.TerminalBuilder
import java.io.IOException

fun createJLineConsole(sender: ConsoleCommandSender): JLineConsole? {
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
        .completer(JLineConsoleCompleter(sender))
        .option(LineReader.Option.DISABLE_EVENT_EXPANSION, true)
        .option(LineReader.Option.INSERT_TAB, false)
        .option(LineReader.Option.AUTO_LIST, true)
        .option(LineReader.Option.AUTO_MENU, true)
        .option(LineReader.Option.AUTO_MENU_LIST, true)
        .option(LineReader.Option.LIST_PACKED, true)
        .option(LineReader.Option.CASE_INSENSITIVE, true)
        .build()

    val console = JLineConsole(terminal, reader, sender)
    Kernel.addStopTask { console.stop() }

    return console
}
