package dev.sectile.boostrap.command

import dev.sectile.boostrap.console.Console
import dev.sectile.engine.api.Engine
import dev.sectile.engine.chat.color.Color
import dev.sectile.engine.chat.component.Message
import dev.sectile.sdk.command.command

fun stopCommand(console: Console?) = command("stop") {
    executes { sender, _ ->
        if (!sender.hasPermission("stop.server")) {
            Message.of("You need be operator to stop the server", Color.RED).sendTo(sender)
            return@executes
        }
        console?.stop()
        sender.receiveMessage("Stopping server...")
    }
}

fun helpCommand() = command("help", "Display help command") {
    arg("command", "Command name to inspect") {
        Engine.commands.all
            .filter { it.permission.isEmpty() || sender.hasPermission(it.permission) }
            .map { it.name }
    }
    executes { sender, args ->
        val target = args.nextString()
        if (target != null) {
            val command = Engine.commands.resolve(target)
            if (command == null || (command.permission.isNotEmpty() && !sender.hasPermission(command.permission))) {
                sender.receiveMessage("Unknown command '/$target'.")
                return@executes
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
                sender.receiveMessage("  Subcommands: ${command.subcommands.map { it.name }.sorted().joinToString(", ")}")
            }
            return@executes
        }

        val visible = Engine.commands.all
            .filter { it.permission.isEmpty() || sender.hasPermission(it.permission) }
            .sortedBy { it.name }

        if (visible.isEmpty()) {
            sender.receiveMessage("No commands available.")
            return@executes
        }

        sender.receiveMessage("Commands (${visible.size}):")
        for (command in visible) {
            val desc = command.description?.let { " - $it" } ?: ""
            sender.receiveMessage("  /${command.name}$desc")
        }
    }
}

fun whereAmICommand() = command("whereami", "Show your current world and position.") {
    executesPlayer { player, _ ->
        player.receiveMessage(
            "World: ${player.world.id} | Position: %.2f, %.2f, %.2f".format(player.x, player.y, player.z),
        )
    }
}

fun whoCommand() = command("who", "List online players") {
    aliases("list", "players")
    executes { sender, _ ->
        val players = Engine.players.all().sortedBy { it.username }
        if (players.isEmpty()) {
            sender.receiveMessage("No players online.")
            return@executes
        }
        val names = players.joinToString(", ") { it.username }
        sender.receiveMessage("Online (${players.size}): $names")
    }
}
