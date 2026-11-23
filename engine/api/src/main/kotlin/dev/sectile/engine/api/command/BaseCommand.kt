package dev.sectile.engine.api.command

import dev.sectile.engine.api.command.argument.CommandArg
import dev.sectile.engine.api.command.argument.CommandFlag
import dev.sectile.engine.api.command.argument.TabContext

abstract class BaseCommand(
    final override val name: String,
    override var description: String? = null,
    aliases: Array<String> = emptyArray(),
    override var permission: String = "",
) : Command {

    init {
        require(name.isNotBlank()) { "Command name must not be blank" }
    }

    private var _aliases: Array<String> = aliases
    private var _args: ArrayList<CommandArg>? = null
    private var _flags: ArrayList<CommandFlag>? = null
    private var _subcommands: ArrayList<Command>? = null

    override val aliases: Array<String>
        get() = _aliases

    override val args: List<CommandArg>
        get() = _args ?: emptyList()

    override val flags: List<CommandFlag>
        get() = _flags ?: emptyList()

    override val subcommands: List<Command>
        get() = _subcommands ?: emptyList()

    override fun findSubcommand(nameOrAlias: String): Command? {
        val subs = _subcommands ?: return null
        for (i in subs.indices) {
            val sub = subs[i]
            if (sub.name.equals(nameOrAlias, ignoreCase = true)) return sub
        }
        for (i in subs.indices) {
            val sub = subs[i]
            val subAliases = sub.aliases
            for (j in subAliases.indices) {
                if (subAliases[j].equals(nameOrAlias, ignoreCase = true)) return sub
            }
        }
        return null
    }

    override fun findFlag(nameOrAlias: String): CommandFlag? {
        val flagList = _flags ?: return null
        val key = stripFlagPrefix(nameOrAlias)

        for (i in flagList.indices) {
            val spec = flagList[i]
            if (spec.name.equals(key, ignoreCase = true)) return spec
        }
        for (i in flagList.indices) {
            val spec = flagList[i]
            val specAliases = spec.aliases
            for (j in specAliases.indices) {
                if (specAliases[j].equals(key, ignoreCase = true)) return spec
            }
        }

        var match: CommandFlag? = null
        for (i in flagList.indices) {
            val spec = flagList[i]
            var matched = spec.name.startsWith(key, ignoreCase = true)
            if (!matched) {
                val specAliases = spec.aliases
                for (j in specAliases.indices) {
                    if (specAliases[j].startsWith(key, ignoreCase = true)) {
                        matched = true
                        break
                    }
                }
            }
            if (matched) {
                if (match != null && match !== spec) return null
                match = spec
            }
        }
        return match
    }

    fun aliases(vararg names: String) {
        if (names.isEmpty()) return
        val current = _aliases
        if (current.isEmpty()) {
            _aliases = Array(names.size) { names[it] }
            return
        }
        val merged = arrayOfNulls<String>(current.size + names.size)
        System.arraycopy(current, 0, merged, 0, current.size)
        System.arraycopy(names, 0, merged, current.size, names.size)
        @Suppress("UNCHECKED_CAST")
        _aliases = merged as Array<String>
    }

    fun permission(node: String?) {
        permission = node ?: ""
    }

    fun arg(
        name: String,
        description: String? = null,
        typeId: String? = null,
        completer: (TabContext.() -> Iterable<String>)? = null,
    ): CommandArg {
        val list = _args ?: ArrayList<CommandArg>(2).also { _args = it }
        return CommandArg(name, description, typeId, completer).also { list.add(it) }
    }

    fun flag(
        name: String,
        vararg aliases: String,
        description: String? = null,
    ): CommandFlag {
        val normalized = stripFlagPrefix(name)
        require(normalized.isNotBlank()) { "Flag name must not be blank" }

        val flagList = _flags ?: ArrayList<CommandFlag>(2).also { _flags = it }
        for (i in flagList.indices) {
            require(!flagList[i].name.equals(normalized, ignoreCase = true)) {
                "Duplicate flag '--$normalized' on command '${this.name}'."
            }
        }

        val cleanedAliases = if (aliases.isEmpty()) {
            emptyArray()
        } else {
            Array(aliases.size) { i ->
                stripFlagPrefix(aliases[i]).also { clean ->
                    require(clean.isNotBlank()) { "Flag alias must not be blank" }
                }
            }
        }

        val spec = CommandFlag(
            name = normalized,
            aliases = cleanedAliases,
            description = description,
        )
        flagList.add(spec)
        return spec
    }

    fun subcommand(command: Command) {
        val subs = _subcommands ?: ArrayList<Command>(2).also { _subcommands = it }
        for (i in subs.indices) {
            require(!subs[i].name.equals(command.name, ignoreCase = true)) {
                "Duplicate subcommand '${command.name}' in command '$name'."
            }
        }
        subs.add(command)
    }

    private fun stripFlagPrefix(raw: String): String = when {
        raw.length >= 2 && raw[0] == '-' && raw[1] == '-' -> raw.substring(2)
        raw.isNotEmpty() && raw[0] == '-' -> raw.substring(1)
        else -> raw
    }
}
