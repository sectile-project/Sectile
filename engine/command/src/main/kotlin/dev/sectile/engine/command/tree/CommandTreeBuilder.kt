package dev.sectile.engine.command.tree

import dev.sectile.engine.api.command.Command
import dev.sectile.engine.api.command.CommandSender
import dev.sectile.engine.api.command.argument.CommandArg
import dev.sectile.network.protocol.interfaceui.CommandArgInfo
import dev.sectile.network.protocol.interfaceui.CommandOptionalArgEntry
import dev.sectile.network.protocol.interfaceui.CommandTreeEntry
import dev.sectile.network.protocol.interfaceui.CommandTreeSyncPacket

internal object CommandTreeBuilder {

    fun argTypeIdFor(commandPath: String, index: Int, arg: CommandArg): String = arg.typeId ?: if (arg.completer != null) "cmd:$commandPath:$index" else "string"

    fun build(sender: CommandSender, commands: Collection<Command>): CommandTreeSyncPacket {
        val entries = ArrayList<CommandTreeEntry>()
        for (command in commands) {
            if (!hasPermission(sender, command.permission)) continue
            entries.add(buildEntry(sender, command, command.name))
        }
        return CommandTreeSyncPacket(entries)
    }

    private fun buildEntry(
        sender: CommandSender,
        command: Command,
        commandPath: String,
    ): CommandTreeEntry {
        val prefix = "/" + commandPath.replace('/', ' ')
        val subEntries = ArrayList<CommandTreeEntry>()
        val subHints = ArrayList<String>()

        for (sub in command.subcommands.values) {
            if (!hasPermission(sender, sub.permission)) continue
            subEntries.add(buildEntry(sender, sub, "$commandPath/${sub.name}"))
            subHints.add(buildSubcommandHint(sub))
        }

        val optionalEntries = command.flags.values.map { flag ->
            CommandOptionalArgEntry(
                name = "--${flag.name}",
                hint = "",
                argTypeId = null,
                description = flag.description,
            )
        }

        val requiredInfos = command.args.mapIndexed { idx, arg ->
            CommandArgInfo(
                name = arg.name,
                argTypeId = argTypeIdFor(commandPath, idx, arg),
                argTypeName = arg.name,
                valueCount = 0,
                description = arg.description,
            )
        }

        return CommandTreeEntry(
            name = command.name,
            aliases = command.aliases.toList(),
            usageText = buildUsageText(prefix, command.args),
            description = command.description,
            subcommands = subEntries,
            subcommandHints = subHints,
            requiredArgs = requiredInfos,
            optionalArgs = optionalEntries,
        )
    }

    private fun buildSubcommandHint(subcommand: Command): String {
        if (subcommand.subcommands.isNotEmpty()) {
            return " ..."
        }
        if (subcommand.args.isNotEmpty()) {
            return subcommand.args.joinToString(" ") { "<${it.name}>" }
        }
        return ""
    }

    private fun buildUsageText(prefix: String, args: List<CommandArg>): String {
        if (args.isEmpty()) return prefix
        return "$prefix ${args.joinToString(" ") { "<${it.name}>" }}"
    }

    private fun hasPermission(sender: CommandSender, permission: String?): Boolean = permission == null || sender.hasPermission(permission)
}
