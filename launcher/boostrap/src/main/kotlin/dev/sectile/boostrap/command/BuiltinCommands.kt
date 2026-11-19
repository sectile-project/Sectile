package dev.sectile.boostrap.command

import dev.sectile.boostrap.console.Console
import dev.sectile.engine.api.Engine
import dev.sectile.engine.api.command.BaseCommand
import dev.sectile.engine.api.command.CommandRegistry
import dev.sectile.engine.api.command.CommandSender
import dev.sectile.engine.api.command.argument.Arguments
import dev.sectile.engine.api.player.Player
import dev.sectile.engine.chat.color.Color
import dev.sectile.engine.chat.component.Message
import dev.sectile.kernel.Kernel

internal class StopCommand(
    private val console: Console?,
) : BaseCommand(
    name = "stop",
) {
    override fun execute(sender: CommandSender, args: Arguments) {
        if (!sender.hasPermission("stop.server")) {
            Message.of("You need be operator to stop the server", Color.RED).sendTo(sender)
            return
        }
        console?.stop()
        sender.receiveMessage("Stopping server...")
    }
}

internal class HelpCommand(
    private val registry: CommandRegistry,
) : BaseCommand(
    name = "help",
    description = "List available commands.",
    aliases = setOf("?"),
) {
    init {
        arg("command", description = "Command name to inspect") {
            registry.commands
                .filter { it.permission == null || sender.hasPermission(it.permission!!) }
                .map { it.name }
        }
    }

    override fun execute(sender: CommandSender, args: Arguments) {
        val target = args[0]
        if (target != null) {
            val command = registry.resolve(target)
            if (command == null || (command.permission != null && !sender.hasPermission(command.permission!!))) {
                sender.receiveMessage("Unknown command '/$target'.")
                return
            }
            val desc = command.description?.let { " - $it" } ?: ""
            sender.receiveMessage("/${command.name}$desc")
            if (command.aliases.isNotEmpty()) {
                sender.receiveMessage("  Aliases: ${command.aliases.joinToString(", ") { "/$it" }}")
            }
            if (command.args.isNotEmpty()) {
                sender.receiveMessage("  Usage: /${command.name} ${command.args.joinToString(" ") { "<${it.name}>" }}")
            }
            if (command.subcommands.isNotEmpty()) {
                sender.receiveMessage("  Subcommands: ${command.subcommands.keys.sorted().joinToString(", ")}")
            }
            return
        }

        val visible = registry.commands
            .filter { it.permission == null || sender.hasPermission(it.permission!!) }
            .sortedBy { it.name }

        if (visible.isEmpty()) {
            sender.receiveMessage("No commands available.")
            return
        }

        sender.receiveMessage("Commands (${visible.size}):")
        for (command in visible) {
            val desc = command.description?.let { " - $it" } ?: ""
            sender.receiveMessage("  /${command.name}$desc")
        }
    }
}

internal class WhereAmICommand :
    BaseCommand(
        name = "whereami",
        description = "Show your current world and position.",
    ) {
    override fun execute(sender: CommandSender, args: Arguments) {
        val player = sender as? Player
        if (player == null) {
            sender.receiveMessage("Only players have a world position.")
            return
        }
        sender.receiveMessage(
            "World: ${player.world.id} | Position: %.2f, %.2f, %.2f".format(player.x, player.y, player.z),
        )
    }
}

internal class WhoCommand :
    BaseCommand(
        name = "who",
        description = "List online players.",
        aliases = setOf("list", "players"),
    ) {
    override fun execute(sender: CommandSender, args: Arguments) {
        val players = Engine.players.all().sortedBy { it.username }
        if (players.isEmpty()) {
            sender.receiveMessage("No players online.")
            return
        }
        val names = players.joinToString(", ") { it.username }
        sender.receiveMessage("Online (${players.size}): $names")
    }
}

internal class PluginsCommand :
    BaseCommand(
        name = "plugins",
        description = "List loaded plugins.",
        aliases = setOf("pl"),
    ) {
    override fun execute(sender: CommandSender, args: Arguments) {
        val plugins = Kernel.pluginCatalog.findAll()
        if (plugins.isEmpty()) {
            sender.receiveMessage("No plugins loaded.")
            return
        }
        val summary = plugins.joinToString(", ") { "${it.metadata.id} (${it.metadata.version}) [${it.state}]" }
        sender.receiveMessage("Plugins (${plugins.size}): $summary")
    }
}
