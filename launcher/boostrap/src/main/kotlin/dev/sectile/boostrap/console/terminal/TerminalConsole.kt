package dev.sectile.boostrap.console.terminal

import dev.sectile.boostrap.console.Console
import dev.sectile.boostrap.terminal.LineReader
import dev.sectile.boostrap.terminal.config.TerminalConfig
import dev.sectile.boostrap.terminal.type.Terminal
import dev.sectile.engine.api.Engine
import dev.sectile.engine.api.command.ConsoleCommandSender

class TerminalConsole(
    private val sender: ConsoleCommandSender,
    config: TerminalConfig,
    private val terminal: Terminal = Terminal.system(),
) : Console {
    @Volatile
    private var run = true

    val lineReader: LineReader = LineReader(terminal, CommandLineCompleter(sender), config)

    override fun run() {
        terminal.use {
            while (run) {
                val raw = lineReader.readLine()
                if (raw == null) {
                    stop()
                    break
                }
                val line = raw.trim()
                if (line.isEmpty()) continue
                if (Engine.isInitialized()) {
                    Engine.commands.execute(sender, line)
                } else if (line.equals("stop", ignoreCase = true)) {
                    lineReader.printAbove("Shutting down...\n")
                    stop()
                    break
                }
            }
        }
    }

    override fun stop() {
        run = false
        terminal.close()
        Runtime.getRuntime().exit(0)
    }
}
