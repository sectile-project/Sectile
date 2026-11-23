package dev.sectile.engine.api.command

import dev.sectile.engine.api.command.argument.Arguments
import dev.sectile.engine.api.command.argument.CommandArg
import dev.sectile.engine.api.command.argument.CommandFlag

interface Command {
    val name: String
    val aliases: Array<String>
    val description: String?
    val permission: String
    val args: List<CommandArg>
    val flags: List<CommandFlag>
    val subcommands: List<Command>
    val hasExecutor: Boolean
        get() = true

    fun findSubcommand(nameOrAlias: String): Command?

    fun findFlag(nameOrAlias: String): CommandFlag?

    fun execute(sender: CommandSender, args: Arguments)
}
