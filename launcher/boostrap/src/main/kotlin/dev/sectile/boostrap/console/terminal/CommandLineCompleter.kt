package dev.sectile.boostrap.console.terminal

import dev.sectile.boostrap.terminal.completer.Completer
import dev.sectile.boostrap.terminal.completer.Completions
import dev.sectile.engine.api.Engine
import dev.sectile.engine.api.command.CommandDispatcher
import dev.sectile.engine.api.command.CommandSender

class CommandLineCompleter(
    private val sender: CommandSender,
    private val dispatcherProvider: () -> CommandDispatcher? = {
        if (Engine.isInitialized()) Engine.commands else null
    },
) : Completer {

    override fun complete(line: String, cursor: Int): Completions {
        val safeCursor = cursor.coerceIn(0, line.length)
        var start = safeCursor
        while (start > 0 && !line[start - 1].isWhitespace()) {
            start--
        }

        val dispatcher = dispatcherProvider() ?: return Completions(start, emptyList())
        val suggestions = dispatcher.suggest(sender, line, safeCursor)
        return Completions(start, suggestions)
    }
}
