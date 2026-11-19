package dev.sectile.engine.api.command

import dev.sectile.engine.api.command.argument.Arguments
import dev.sectile.engine.api.command.argument.CommandArg
import dev.sectile.engine.api.command.argument.CommandFlag
import dev.sectile.engine.api.command.argument.TabContext
import java.util.Locale

abstract class BaseCommand(
    name: String,
    override var description: String? = null,
    aliases: Iterable<String> = emptySet(),
    override var permission: String? = null,
) : Command {

    final override val name: String = name.lowercase(Locale.ROOT).also {
        require(it.isNotBlank()) { "Command name must not be blank" }
    }

    private val _aliases = LinkedHashSet<String>().apply {
        aliases.forEach { add(it.lowercase(Locale.ROOT)) }
    }
    private val _args = ArrayList<CommandArg>()
    private val _flags = LinkedHashMap<String, CommandFlag>()
    private val flagAliases = HashMap<String, CommandFlag>()
    private val _subcommands = LinkedHashMap<String, Command>()
    private val subcommandAliases = HashMap<String, Command>()
    protected var customExecutor: ((CommandSender, Arguments) -> Unit)? = null
    private val overridesExecute: Boolean =
        javaClass.getMethod("execute", CommandSender::class.java, Arguments::class.java).declaringClass != BaseCommand::class.java

    override val hasExecutor: Boolean
        get() = customExecutor != null || overridesExecute

    override val aliases: Set<String>
        get() = _aliases

    override val args: List<CommandArg>
        get() = _args

    override val flags: Map<String, CommandFlag>
        get() = _flags

    override val subcommands: Map<String, Command>
        get() = _subcommands

    override fun execute(sender: CommandSender, args: Arguments) {
        customExecutor?.invoke(sender, args)
    }

    override fun findSubcommand(nameOrAlias: String): Command? {
        val key = nameOrAlias.lowercase(Locale.ROOT)
        return _subcommands[key] ?: subcommandAliases[key]
    }

    override fun findFlag(nameOrAlias: String): CommandFlag? {
        val key = nameOrAlias.removePrefix("--").removePrefix("-").lowercase(Locale.ROOT)
        _flags[key]?.let { return it }
        flagAliases[key]?.let { return it }

        val matches = LinkedHashSet<CommandFlag>()
        for ((flagName, spec) in _flags) {
            if (flagName.startsWith(key)) matches.add(spec)
        }
        for ((alias, spec) in flagAliases) {
            if (alias.startsWith(key)) matches.add(spec)
        }
        return if (matches.size == 1) matches.first() else null
    }

    fun aliases(vararg names: String) {
        names.forEach { _aliases.add(it.lowercase(Locale.ROOT)) }
    }

    fun permission(node: String?) {
        permission = node
    }

    fun arg(
        name: String,
        description: String? = null,
        typeId: String? = null,
        completer: (TabContext.() -> Iterable<String>)? = null,
    ): CommandArg = CommandArg(name, description, typeId, completer).also { _args.add(it) }

    fun flag(
        name: String,
        vararg aliases: String,
        description: String? = null,
    ): CommandFlag {
        val normalized = normalizeFlagName(name)
        require(!_flags.containsKey(normalized)) {
            "Duplicate flag '--$normalized' on command '$name'."
        }
        val spec = CommandFlag(
            name = normalized,
            aliases = aliases.map(::normalizeFlagName).toSet(),
            description = description,
        )
        _flags[normalized] = spec
        spec.aliases.forEach { alias -> flagAliases[alias] = spec }
        return spec
    }

    fun subcommand(command: Command) {
        val key = command.name.lowercase(Locale.ROOT)
        require(!_subcommands.containsKey(key)) {
            "Duplicate subcommand '$key' in command '$name'."
        }
        _subcommands[key] = command
        command.aliases.forEach { alias -> subcommandAliases[alias.lowercase(Locale.ROOT)] = command }
    }

    fun executes(block: (CommandSender, Arguments) -> Unit) {
        customExecutor = block
    }

    private fun normalizeFlagName(raw: String): String {
        val normalized = raw.removePrefix("--").removePrefix("-").lowercase(Locale.ROOT)
        require(normalized.isNotBlank()) { "Flag name must not be blank" }
        return normalized
    }
}
