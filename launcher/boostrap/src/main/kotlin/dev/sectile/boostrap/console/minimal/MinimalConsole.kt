package dev.sectile.boostrap.console.minimal

import dev.sectile.boostrap.console.Console
import java.io.BufferedReader
import java.io.InputStreamReader

class MinimalConsole : Console {
    private var run = true

    override fun run() {
        val input = BufferedReader(InputStreamReader(System.`in`))
        while (run) {
            System.out.flush()
            val line = input.readLine()?.trim() ?: break // null = EOF (Ctrl+D / Ctrl+Z)
            when {
                line.isEmpty() -> continue
                line == "stop" -> stop()
                else -> println("Unknown command: $line")
            }
        }
    }

    override fun stop() {
        run = false
        Runtime.getRuntime().exit(0)
    }
}
