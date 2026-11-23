package dev.sectile.engine.command.tree

import dev.sectile.engine.api.command.Command
import dev.sectile.engine.api.command.CommandSender
import dev.sectile.engine.api.command.argument.CommandArg
import dev.sectile.network.protocol.command.outbound.CommandTreeSyncPacket
import dev.sectile.network.protocol.command.structures.CommandArgInfo
import dev.sectile.network.protocol.command.structures.CommandOptionalArgEntry
import dev.sectile.network.protocol.command.structures.CommandTreeEntry

internal object CommandTreeBuilder {

    fun argTypeIdFor(commandPath: String, index: Int, arg: CommandArg): String = arg.typeId ?: if (arg.completer != null) "cmd:$commandPath:$index" else "string"

    fun build(sender: CommandSender, commands: Collection<Command>): CommandTreeSyncPacket {
        if (commands.isEmpty()) return CommandTreeSyncPacket(emptyList())
        val entries = ArrayList<CommandTreeEntry>(commands.size)
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
        val subEntries: List<CommandTreeEntry>
        val subHints: List<String>

        if (command.subcommands.isEmpty()) {
            subEntries = emptyList()
            subHints = emptyList()
        } else {
            val builtEntries = ArrayList<CommandTreeEntry>(command.subcommands.size)
            val builtHints = ArrayList<String>(command.subcommands.size)
            for (sub in command.subcommands) {
                if (!hasPermission(sender, sub.permission)) continue
                builtEntries.add(buildEntry(sender, sub, "$commandPath/${sub.name}"))
                builtHints.add(buildSubcommandHint(sub))
            }
            subEntries = builtEntries
            subHints = builtHints
        }

        val optionalEntries = if (command.flags.isEmpty()) {
            emptyList()
        } else {
            command.flags.map { flag ->
                CommandOptionalArgEntry(
                    name = "--${flag.name}",
                    hint = "",
                    argTypeId = null,
                    description = flag.description,
                )
            }
        }

        val requiredInfos = if (command.args.isEmpty()) {
            emptyList()
        } else {
            command.args.mapIndexed { idx, arg ->
                CommandArgInfo(
                    name = arg.name,
                    argTypeId = argTypeIdFor(commandPath, idx, arg),
                    argTypeName = arg.name,
                    valueCount = 0,
                    description = arg.description,
                )
            }
        }

        return CommandTreeEntry(
            name = command.name,
            aliases = if (command.aliases.isEmpty()) emptyList() else command.aliases.asList(),
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
