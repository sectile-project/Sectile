package dev.sectile.boostrap.command

import dev.sectile.engine.api.command.CommandSender
import dev.sectile.engine.chat.color.Color
import dev.sectile.engine.chat.component.Message
import dev.sectile.kernel.Kernel
import dev.sectile.kernel.plugin.model.PluginId
import dev.sectile.sdk.command.CommandBuilder
import dev.sectile.sdk.command.command
import dev.sectile.sdk.command.subcommand

fun pluginCommand() = command("plugin", "a") {
    aliases("pl")

    executes { sender, _ -> listPlugins(sender) }

    subcommand("list", "List registered plugins.") {
        executes { sender, _ -> listPlugins(sender) }
    }

    subcommand("reload", "Reload all plugins or a single plugin.") {
        permission("plugins.manage")
        argListPlugin()
        executes { sender, args ->
            runManaged(sender) {
                val target = args.nextString()
                if (target == null) {
                    Kernel.pluginLifecycleControl.reloadAll()
                    sender.receiveMessage("Reloaded plugins (${Kernel.pluginCatalog.findAll().size} registered).")
                } else {
                    val id = PluginId(target)
                    Kernel.pluginLifecycleControl.reload(id)
                    sender.receiveMessage("Reloaded plugin $id.")
                }
            }
        }
    }

    subcommand("enable", "Enable a plugin.") {
        permission("plugins.manage")
        argListPlugin()
        executes { sender, args ->
            runManaged(sender) {
                val target = args.nextString()
                if (target == null) {
                    sender.receiveMessage("Usage: /plugins enable <plugin>")
                    return@runManaged
                }
                val id = PluginId(target)
                Kernel.pluginLifecycleControl.enable(id)
                sender.receiveMessage("Enabled plugin $id.")
            }
        }
    }

    subcommand("disable", "Disable a plugin.") {
        permission("plugins.manage")
        argListPlugin()
        executes { sender, args ->
            runManaged(sender) {
                val target = args.nextString()
                if (target == null) {
                    sender.receiveMessage("Usage: /plugins disable <plugin>")
                    return@runManaged
                }
                val id = PluginId(target)
                Kernel.pluginLifecycleControl.disable(id)
                sender.receiveMessage("Disabled plugin $id.")
            }
        }
    }
}

private fun CommandBuilder.argListPlugin() {
    arg("plugin", completer = { Kernel.pluginCatalog.findAll().map { it.metadata.id.value } })
}

private fun listPlugins(sender: CommandSender) {
    val plugins = Kernel.pluginCatalog.findAll()
    if (plugins.isEmpty()) {
        sender.receiveMessage("No plugins loaded.")
        return
    }
    val summary = plugins.joinToString(", ") { "${it.metadata.id} (${it.metadata.version}) [${it.state}]" }
    sender.receiveMessage("Plugins (${plugins.size}): $summary")
}

private inline fun runManaged(sender: CommandSender, action: () -> Unit) {
    if (!sender.hasPermission("plugins.manage")) {
        Message.of("You do not have permission to manage plugins.", Color.RED).sendTo(sender)
        return
    }
    runCatching(action).onFailure { error ->
        Message.of("Plugin action failed: ${error.message ?: error.javaClass.simpleName}", Color.RED).sendTo(sender)
    }
}
