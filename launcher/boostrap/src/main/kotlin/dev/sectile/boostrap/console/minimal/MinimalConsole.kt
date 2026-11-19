package dev.sectile.boostrap.console.minimal

import dev.sectile.boostrap.console.Console
import dev.sectile.engine.api.Engine
import dev.sectile.engine.api.command.ConsoleCommandSender
import java.io.BufferedReader
import java.io.InputStreamReader

class MinimalConsole(
    private val sender: ConsoleCommandSender,
) : Console {
    @Volatile
    private var run = true

    override fun run() {
        val input = BufferedReader(InputStreamReader(System.`in`))
        while (run) {
            System.out.flush()
            val line = input.readLine()?.trim() ?: break // null = EOF (Ctrl+D / Ctrl+Z)
            if (line.isEmpty()) continue
            if (Engine.isInitialized()) {
                Engine.commands.execute(sender, line)
            } else if (line == "stop") {
                println("Shutting down...")
                stop()
                break
            }
        }
    }

    override fun stop() {
        run = false
        Runtime.getRuntime().exit(0)
    }
}
