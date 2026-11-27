package dev.sectile.engine.command.completion

import dev.sectile.engine.api.command.Command
import dev.sectile.engine.api.command.CommandSender
import dev.sectile.engine.api.command.argument.TabContext
import dev.sectile.engine.command.parser.CommandParser

internal object CommandCompleter {

    fun suggestLine(
        sender: CommandSender,
        allCommands: Collection<Command>,
        resolver: (String) -> Command?,
        commandLine: String,
        cursorIndex: Int = commandLine.length,
    ): List<String> {
        val beforeCursor = commandLine.substring(0, cursorIndex.coerceIn(0, commandLine.length))
        val tokens = splitForCompletion(beforeCursor)
        if (tokens.isEmpty()) {
            return suggestTokens(sender, allCommands, resolver, listOf(""), 0)
        }
        return suggestTokens(sender, allCommands, resolver, tokens, tokens.lastIndex)
    }

    fun suggestTokens(
        sender: CommandSender,
        allCommands: Collection<Command>,
        resolver: (String) -> Command?,
        words: List<String>,
        wordIndex: Int,
    ): List<String> {
        if (wordIndex < 0 || wordIndex >= words.size) return emptyList()

        val rawPrefix = words[wordIndex]
        if (wordIndex == 0) {
            return suggestCommandNames(sender, allCommands, rawPrefix)
        }

        val rootName = words[0].removePrefix("/")
        var command = resolver(rootName) ?: return emptyList()
        if (!hasPermission(sender, command.permission)) return emptyList()

        var argBase = 1
        for (i in 1 until wordIndex) {
            if (CommandParser.isLongOption(words[i])) break
            val sub = command.findSubcommand(words[i]) ?: break
            if (!hasPermission(sender, sub.permission)) break
            command = sub
            argBase = i + 1
        }

        var suggestions: LinkedHashSet<String>? = null

        if (CommandParser.isLongOption(rawPrefix)) {
            if (command.flags.isEmpty()) return emptyList()
            val out = LinkedHashSet<String>()
            suggestFlagNames(command, rawPrefix, out)
            return out.toList()
        }

        val positionalIndex = countPositionalTokens(command, words, argBase, wordIndex)
        if (positionalIndex == 0 && command.subcommands.isNotEmpty()) {
            for (subCmd in command.subcommands) {
                val subName = subCmd.name
                if (hasPermission(sender, subCmd.permission) && subName.startsWith(rawPrefix, ignoreCase = true)) {
                    val out = suggestions ?: LinkedHashSet<String>().also { suggestions = it }
                    out.add(subName)
                }
            }
        }

        val arg = command.args.getOrNull(positionalIndex)
        if (arg?.completer != null) {
            val out = suggestions ?: LinkedHashSet<String>().also { suggestions = it }
            collectMatches(command.javaClass.classLoader, arg.completer, sender, rawPrefix, out)
        }

        return suggestions?.toList() ?: emptyList()
    }

    private fun suggestCommandNames(
        sender: CommandSender,
        allCommands: Collection<Command>,
        rawPrefix: String,
    ): List<String> {
        val hasSlash = rawPrefix.startsWith("/")
        val prefix = if (hasSlash) rawPrefix.substring(1) else rawPrefix
        val out = ArrayList<String>()
        for (command in allCommands) {
            if (!hasPermission(sender, command.permission)) continue
            if (command.name.startsWith(prefix, ignoreCase = true)) {
                out.add(if (hasSlash) "/${command.name}" else command.name)
            }
        }
        return out
    }

    private fun suggestFlagNames(
        command: Command,
        prefix: String,
        out: MutableSet<String>,
    ) {
        for (flag in command.flags) {
            val full = "--${flag.name}"
            if (full.startsWith(prefix, ignoreCase = true)) {
                out.add(full)
            }
        }
    }

    private fun countPositionalTokens(
        command: Command,
        words: List<String>,
        argBase: Int,
        wordIndex: Int,
    ): Int {
        var count = 0
        for (i in argBase until wordIndex) {
            val token = words[i]
            if (CommandParser.isLongOption(token)) continue
            if (token.startsWith("-") && token.length > 1 && command.findFlag(token) != null) continue
            count++
        }
        return count
    }

    private fun collectMatches(
        classLoader: ClassLoader?,
        completer: (TabContext.() -> Iterable<String>)?,
        sender: CommandSender,
        partial: String,
        out: MutableSet<String>,
    ) {
        if (completer == null) return
        val filter = partial.trim()
        val thread = Thread.currentThread()
        val previous = thread.contextClassLoader
        val switchLoader = classLoader != null && previous !== classLoader
        if (switchLoader) thread.contextClassLoader = classLoader
        val raw = try {
            TabContext(sender, partial).completer()
        } finally {
            if (switchLoader) thread.contextClassLoader = previous
        }
        for (candidate in raw) {
            if (candidate.startsWith(filter, ignoreCase = true)) {
                out.add(candidate)
            }
        }
    }

    private fun splitForCompletion(beforeCursor: String): List<String> {
        val trimmed = beforeCursor.trimStart()
        if (trimmed.isEmpty()) return emptyList()

        val tokens = ArrayList<String>()
        val current = StringBuilder()
        var inSpace = false
        for (c in trimmed) {
            if (c.isWhitespace()) {
                if (!inSpace) {
                    tokens.add(current.toString())
                    current.setLength(0)
                    inSpace = true
                }
            } else {
                current.append(c)
                inSpace = false
            }
        }
        tokens.add(current.toString())
        return tokens
    }

    private fun hasPermission(sender: CommandSender, permission: String): Boolean = permission.isEmpty() || sender.hasPermission(permission)
}
