package dev.sectile.sdk.command

import dev.sectile.engine.api.Engine
import dev.sectile.engine.api.command.BaseCommand
import dev.sectile.engine.api.command.Command
import dev.sectile.engine.api.command.CommandRegistration
import dev.sectile.engine.api.command.CommandRegistry
import dev.sectile.engine.api.command.CommandSender
import dev.sectile.engine.api.command.argument.Arguments
import dev.sectile.engine.api.command.argument.CommandArg
import dev.sectile.engine.api.player.Player
import java.util.Locale

@DslMarker
annotation class CommandDslMarker

@CommandDslMarker
class CommandBuilder(
    name: String,
    description: String? = null,
) : BaseCommand(name, description) {

    override val hasExecutor: Boolean
        get() = customExecutor != null

    fun executesPlayer(
        rejectMessage: String = "Only players can execute this command.",
        block: (Player, Arguments) -> Unit,
    ) {
        executes { sender, args ->
            val player = sender as? Player
            if (player == null) {
                sender.receiveMessage(rejectMessage)
            } else {
                block(player, args)
            }
        }
    }

    fun executesConsole(
        rejectMessage: String = "Only the console can execute this command.",
        block: (CommandSender, Arguments) -> Unit,
    ) {
        executes { sender, args ->
            if (sender is Player) {
                sender.receiveMessage(rejectMessage)
            } else {
                block(sender, args)
            }
        }
    }
}

fun command(
    name: String,
    description: String? = null,
    block: CommandBuilder.() -> Unit,
): Command = CommandBuilder(name, description).apply(block)

fun CommandRegistry.register(
    name: String,
    description: String? = null,
    block: CommandBuilder.() -> Unit,
): CommandRegistration = register(command(name, description, block))

fun CommandSender.asPlayerOrNull(): Player? = this as? Player

fun BaseCommand.arg(
    name: String,
    choices: Iterable<String>,
    description: String? = null,
): CommandArg {
    val list = choices.toList()
    return arg(name, description) { list }
}

fun BaseCommand.argChoices(
    name: String,
    vararg choices: String,
    description: String? = null,
): CommandArg = arg(name, choices.asList(), description)

fun BaseCommand.args(vararg names: String) {
    names.forEach { arg(it) }
}

fun BaseCommand.argPlayer(name: String = "player", description: String? = null): CommandArg = arg(name, description, typeId = "player") { Engine.players.all().map { it.username } }

fun BaseCommand.argWorld(name: String = "world", description: String? = null): CommandArg = arg(name, description, typeId = "world") { Engine.worlds.all().map { it.id } }

fun BaseCommand.argBoolean(name: String = "value", description: String? = null): CommandArg = arg(name, description, typeId = "boolean") { listOf("true", "false") }

fun <E : Enum<E>> BaseCommand.argEnum(
    name: String,
    enumClass: Class<E>,
    description: String? = null,
): CommandArg {
    val names = enumClass.enumConstants?.map { it.name.lowercase(Locale.ROOT) } ?: emptyList()
    return arg(name, description) { names }
}

inline fun <reified E : Enum<E>> BaseCommand.argEnum(
    name: String = E::class.java.simpleName.lowercase(Locale.ROOT),
    description: String? = null,
): CommandArg = argEnum(name, E::class.java, description)

fun BaseCommand.flags(vararg names: String) {
    names.forEach { flag(it) }
}

fun BaseCommand.subcommand(
    name: String,
    description: String? = null,
    block: CommandBuilder.() -> Unit,
): Command = command(name, description, block).also(::subcommand)
