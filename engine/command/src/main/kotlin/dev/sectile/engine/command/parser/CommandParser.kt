package dev.sectile.engine.command.parser

import dev.sectile.engine.api.command.Command
import dev.sectile.engine.api.command.CommandSender
import dev.sectile.engine.api.command.argument.Arguments
import dev.sectile.engine.api.command.argument.CommandFlag
import java.util.Locale

internal object CommandParser {

    private val LONG_FLAG_REGEX = Regex("^--([\\w-]+|\\?)(?:=(.*))?$", RegexOption.DOT_MATCHES_ALL)

    fun isLongOption(token: String): Boolean = token.startsWith("--")

    fun parseAndExecute(
        sender: CommandSender,
        rootCommand: Command,
        tokens: List<String>,
    ): Boolean {
        if (tokens.isEmpty()) return false

        var currentCommand = rootCommand
        var index = 1

        while (index < tokens.size && !isLongOption(tokens[index])) {
            val sub = currentCommand.findSubcommand(tokens[index]) ?: break
            currentCommand = sub
            index++
        }

        val positionalTokens = ArrayList<String>()
        val flags = HashSet<String>()

        for (i in index until tokens.size) {
            val token = tokens[i]

            if (isLongOption(token)) {
                val match = LONG_FLAG_REGEX.matchEntire(token)
                    ?: throw IllegalArgumentException("Malformed flag '$token'.")
                val rawName = match.groupValues[1].lowercase(Locale.ROOT)
                if (match.groups[2] != null) {
                    val flagName = currentCommand.findFlag(rawName)?.name ?: rawName
                    throw IllegalArgumentException("Flag '--$flagName' does not take a value.")
                }

                if (rawName == "help" || rawName == "?") {
                    sendCommandHelp(sender, currentCommand, tokens, index)
                    return true
                }

                recordFlag(flags, currentCommand.findFlag(rawName), rawName)
            } else if (token.startsWith("-") && token.length > 1) {
                // Only treat single-dash tokens like `-s` as flags when declared,
                // leaving negative numbers such as `-4` as positional arguments.
                val declared = currentCommand.findFlag(token)
                if (declared != null) {
                    recordFlag(flags, declared, declared.name)
                } else {
                    positionalTokens.add(token)
                }
            } else {
                positionalTokens.add(token)
            }
        }

        if (positionalTokens.isEmpty() && !currentCommand.hasExecutor && currentCommand.subcommands.isNotEmpty()) {
            sendCommandHelp(sender, currentCommand, tokens, index)
            return true
        }

        if (!currentCommand.hasExecutor) {
            val prefix = formatCommandPath(rootCommand, tokens, index)
            sender.receiveMessage("Invalid arguments. Usage: ${buildUsageSummary(currentCommand, prefix)}")
            return false
        }

        val args = Arguments(
            tokens = positionalTokens,
            flags = flags,
        )

        return try {
            currentCommand.execute(sender, args)
            true
        } catch (ex: IllegalArgumentException) {
            sender.receiveMessage(ex.message ?: "Invalid command arguments.")
            false
        }
    }

    private fun recordFlag(flagsOut: MutableSet<String>, flag: CommandFlag?, rawName: String) {
        if (flag == null) {
            flagsOut.add(rawName)
            return
        }
        flagsOut.add(flag.name)
        flagsOut.addAll(flag.aliases)
    }

    private fun formatCommandPath(rootCommand: Command, tokens: List<String>, endIndex: Int): String = buildString {
        append('/').append(rootCommand.name)
        var current = rootCommand
        for (i in 1 until endIndex) {
            current = current.findSubcommand(tokens[i]) ?: break
            append(' ').append(current.name)
        }
    }

    private fun sendCommandHelp(sender: CommandSender, command: Command, tokens: List<String>, endIndex: Int) {
        val prefix = formatCommandPath(tokens, endIndex)
        val descriptionSuffix = command.description?.let { " - $it" } ?: ""
        sender.receiveMessage("$prefix$descriptionSuffix")

        if (command.hasExecutor) {
            sender.receiveMessage("  Usage: ${formatUsageLine(prefix, command)}")
        }

        val visibleSubs = command.subcommands.values.filter {
            it.permission == null || sender.hasPermission(it.permission!!)
        }
        if (visibleSubs.isNotEmpty()) {
            sender.receiveMessage("  Subcommands:")
            for (sub in visibleSubs) {
                val subDesc = sub.description?.let { " - $it" } ?: ""
                sender.receiveMessage("    ${formatUsageLine("$prefix ${sub.name}", sub)}$subDesc")
            }
        }

        if (command.flags.isNotEmpty()) {
            sender.receiveMessage("  Flags:")
            for ((name, _, description) in command.flags.values) {
                val flagDesc = description?.let { " - $it" } ?: ""
                sender.receiveMessage("    --$name$flagDesc")
            }
        }
    }

    private fun formatCommandPath(tokens: List<String>, endIndex: Int): String = buildString {
        append('/')
        for (i in 0 until endIndex) {
            if (i > 0) append(' ')
            append(tokens[i].lowercase(Locale.ROOT))
        }
    }

    private fun buildUsageSummary(command: Command, prefix: String): String {
        if (command.hasExecutor) {
            return formatUsageLine(prefix, command)
        }
        if (command.subcommands.isNotEmpty()) {
            return "$prefix <${command.subcommands.keys.joinToString("|")}>"
        }
        return prefix
    }

    private fun formatUsageLine(prefix: String, command: Command): String {
        if (command.args.isEmpty()) return prefix
        return buildString {
            append(prefix)
            for ((name) in command.args) {
                append(" <").append(name).append('>')
            }
        }
    }
}
