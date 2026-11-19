package dev.sectile.boostrap.console.jline

import dev.sectile.boostrap.console.Console
import dev.sectile.engine.api.Engine
import dev.sectile.engine.api.command.ConsoleCommandSender
import org.jline.reader.EndOfFileException
import org.jline.reader.LineReader
import org.jline.reader.MaskingCallback
import org.jline.reader.UserInterruptException
import org.jline.terminal.Terminal
import java.io.IOException

class JLineConsole(
    val terminal: Terminal,
    val reader: LineReader,
    private val sender: ConsoleCommandSender,
) : Console {

    @Volatile
    private var run = true

    override fun run() {
        while (run) {
            val line: String
            try {
                line = reader.readLine("> ", null, null as MaskingCallback?, null).trim { it <= ' ' }
            } catch (_: UserInterruptException) {
                stop()
                break
            } catch (_: EndOfFileException) {
                stop()
                break
            }

            if (line.isEmpty()) {
                continue
            }

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
        if (!run) {
            return
        }

        try {
            terminal.close()
        } catch (e: IOException) {
            System.err.println("Error trying to stop the terminal")
            e.printStackTrace(System.err)
        } finally {
            run = false
            Runtime.getRuntime().exit(0)
        }
    }
}
