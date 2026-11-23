package dev.sectile.engine.command

import dev.sectile.engine.api.Engine
import dev.sectile.engine.api.command.Command
import dev.sectile.engine.api.command.CommandRegistration
import dev.sectile.engine.api.command.CommandRegistry
import dev.sectile.engine.api.command.CommandSender
import dev.sectile.engine.api.command.argument.TabContext
import dev.sectile.engine.api.player.Player
import dev.sectile.engine.command.completion.CommandCompleter
import dev.sectile.engine.command.parser.CommandParser
import dev.sectile.engine.command.tokenizer.CommandTokenizer
import dev.sectile.engine.command.tree.CommandTreeBuilder
import dev.sectile.kernel.Kernel
import dev.sectile.kernel.logger.Logger
import dev.sectile.network.api.Network
import dev.sectile.network.protocol.command.outbound.ArgCacheInvalidationPacket
import java.util.concurrent.ConcurrentHashMap

class DefaultCommandRegistry(
    private val logger: Logger = Kernel.globalLogger,
) : CommandRegistry {

    private val commandsByName = ConcurrentHashMap<String, Command>()
    private val aliasesToName = ConcurrentHashMap<String, String>()
    private val completersById = ConcurrentHashMap<String, TabContext.() -> Iterable<String>>()
    private val keysByCommand = ConcurrentHashMap<Command, Set<String>>()
    private val registrationLock = Any()

    init {
        completersById["string"] = { emptyList() }
        completersById["boolean"] = { listOf("true", "false") }
        completersById["player"] = {
            if (Engine.isInitialized()) Engine.players.all().map { it.username } else emptyList()
        }
        completersById["world"] = {
            if (Engine.isInitialized()) Engine.worlds.all().map { it.id } else emptyList()
        }
    }

    override val all: Collection<Command>
        get() = commandsByName.values.toList()

    override fun register(command: Command): CommandRegistration {
        val name = command.name
        require(name.isNotBlank()) { "Registered command must have a non-blank name." }

        synchronized(registrationLock) {
            registerInMap(command)
        }

        syncCommandTreeToAll()
        return DefaultRegistration(command)
    }

    override fun register(vararg commands: Command): List<CommandRegistration> {
        val registrations = mutableListOf<CommandRegistration>()
        synchronized(registrationLock) {
            commands.map { command ->
                registerInMap(command)
                registrations.add(DefaultRegistration(command))
            }
        }
        syncCommandTreeToAll()
        return registrations
    }

    private fun registerInMap(command: Command) {
        val name = command.name
        require(name.isNotBlank()) { "Registered command must have a non-blank name." }
        val previousKey = findCommandKey(name)
        val previous = if (previousKey != null) commandsByName.remove(previousKey) else null
        if (previous != null) {
            removeAliasesFor(previous)
            unregisterCommandCompleters(previous)
        }
        commandsByName[name] = command
        registerAliases(name, command.aliases)
        registerCommandCompleters(command)
    }

    override fun unregister(name: String): Boolean {
        val removed = synchronized(registrationLock) {
            val command = commandsByName.remove(name)
                ?: findCommandKey(name)?.let { commandsByName.remove(it) }
                ?: return false
            removeAliasesFor(command)
            unregisterCommandCompleters(command)
            command
        }
        logger.debug("Unregistered command '/${removed.name}'")
        syncCommandTreeToAll()
        return true
    }

    override fun unregister(command: Command): Boolean {
        val removed = synchronized(registrationLock) {
            val removedSuccess = commandsByName.remove(command.name, command) ||
                findCommandKey(command.name)?.let { commandsByName.remove(it, command) } == true
            if (removedSuccess) {
                removeAliasesFor(command)
                unregisterCommandCompleters(command)
            }
            removedSuccess
        }
        if (removed) {
            syncCommandTreeToAll()
        }
        return removed
    }

    override fun resolve(nameOrAlias: String): Command? {
        commandsByName[nameOrAlias]?.let { return it }
        aliasesToName[nameOrAlias]?.let { canonical ->
            commandsByName[canonical]?.let { return it }
        }
        for ((key, cmd) in commandsByName) {
            if (key.equals(nameOrAlias, ignoreCase = true)) return cmd
        }
        for ((alias, canonical) in aliasesToName) {
            if (alias.equals(nameOrAlias, ignoreCase = true)) {
                return commandsByName[canonical]
            }
        }
        return null
    }

    override fun completeArgType(
        sender: CommandSender,
        argTypeId: String,
        partial: String,
    ): List<String> {
        val completer = completersById[argTypeId] ?: return emptyList()
        val filter = partial.trim()
        return TabContext(sender, partial)
            .completer()
            .asSequence()
            .filter { it.startsWith(filter, ignoreCase = true) }
            .distinct()
            .toList()
    }

    override fun invalidateArgCache(vararg argTypeIds: String) {
        if (argTypeIds.isEmpty() || !Engine.isInitialized()) return
        val connections = Engine.players.all().mapNotNull { it.connection.takeIf { conn -> conn.isOnline() } }
        if (connections.isEmpty()) return

        val packet = ArgCacheInvalidationPacket(argTypeIds.toList())
        if (connections.size > 1 && Network.isInitialized()) {
            Network.packetsSender.sendPacket(connections, packet)
        } else {
            for (connection in connections) {
                connection.sendPacket(packet)
            }
        }
    }

    override fun syncCommandTree(player: Player) {
        if (!player.connection.isOnline()) return
        val packet = CommandTreeBuilder.build(player, commandsByName.values)
        player.connection.sendPacket(packet)
    }

    override fun syncCommandTreeToAll() {
        if (!Engine.isInitialized()) return
        val players = Engine.players.all()
        if (players.isEmpty()) return
        for (player in players) {
            syncCommandTree(player)
        }
    }

    override fun execute(sender: CommandSender, commandLine: String): Boolean {
        val tokens = try {
            CommandTokenizer.tokenize(commandLine)
        } catch (ex: IllegalArgumentException) {
            sender.receiveMessage(ex.message ?: "Invalid command syntax.")
            return false
        }

        if (tokens.isEmpty()) {
            return false
        }

        val commandName = tokens[0]
        val command = resolve(commandName)
        if (command == null) {
            sender.receiveMessage("Unknown command '/$commandName'. Type /help for a list of commands.")
            return false
        }

        val rawCommand = commandLine.trim().removePrefix("/").trimStart()
        logger.info("${sender.name} executed command: /$rawCommand")

        return try {
            withCommandClassLoader(command.javaClass.classLoader) {
                CommandParser.parseAndExecute(sender, command, tokens)
            }
        } catch (ex: IllegalArgumentException) {
            sender.receiveMessage(ex.message ?: "Invalid command arguments.")
            false
        } catch (thrown: Throwable) {
            logger.error("Failed to execute command '/$rawCommand' for ${sender.name}", thrown)
            val detail = thrown.message?.takeIf { it.isNotBlank() } ?: thrown.javaClass.simpleName
            sender.receiveMessage("An error occurred while executing '/$commandName': $detail")
            false
        }
    }

    override fun suggest(
        sender: CommandSender,
        commandLine: String,
        cursorIndex: Int,
    ): List<String> = CommandCompleter.suggestLine(
        sender = sender,
        allCommands = commandsByName.values,
        resolver = ::resolve,
        commandLine = commandLine,
        cursorIndex = cursorIndex,
    )

    override fun suggestTokens(
        sender: CommandSender,
        words: List<String>,
        wordIndex: Int,
    ): List<String> = CommandCompleter.suggestTokens(
        sender = sender,
        allCommands = commandsByName.values,
        resolver = ::resolve,
        words = words,
        wordIndex = wordIndex,
    )

    private fun registerAliases(canonicalName: String, aliases: Array<String>) {
        if (aliases.isEmpty()) return
        for (i in aliases.indices) {
            val alias = aliases[i]
            if (alias.isBlank()) continue
            if (alias.equals(canonicalName, ignoreCase = true)) continue

            var duplicate = false
            for (j in 0 until i) {
                if (aliases[j].equals(alias, ignoreCase = true)) {
                    duplicate = true
                    break
                }
            }
            if (duplicate) continue

            val existingKey = findAliasKey(alias)
            if (existingKey != null && existingKey != alias) {
                aliasesToName.remove(existingKey)
            }
            aliasesToName[alias] = canonicalName
        }
    }

    private fun registerCommandCompleters(command: Command) {
        if (command.args.isEmpty() && command.subcommands.isEmpty()) return
        val keys = collectCommandCompleters(command, command.name, command.javaClass.classLoader, null)
        if (keys != null) {
            keysByCommand[command] = keys
        }
    }

    private fun collectCommandCompleters(
        command: Command,
        path: String,
        classLoader: ClassLoader?,
        registeredKeys: LinkedHashSet<String>?,
    ): LinkedHashSet<String>? {
        var keys = registeredKeys
        val args = command.args
        for (idx in args.indices) {
            val arg = args[idx]
            val completer = arg.completer ?: continue
            val key = CommandTreeBuilder.argTypeIdFor(path, idx, arg)
            if (key in BUILTIN_COMPLETER_KEYS) continue
            completersById.putIfAbsent(key) {
                withCommandClassLoader(classLoader) { completer() }
            }
            val set = keys ?: LinkedHashSet<String>(4).also { keys = it }
            set.add(key)
        }
        val subs = command.subcommands
        if (subs.isNotEmpty()) {
            for (sub in subs) {
                keys = collectCommandCompleters(sub, "$path/${sub.name}", classLoader, keys)
            }
        }
        return keys
    }

    private fun unregisterCommandCompleters(command: Command) {
        if (keysByCommand.isEmpty()) return
        keysByCommand.remove(command)?.forEach { completersById.remove(it) }
    }

    private fun removeAliasesFor(command: Command) {
        val aliases = command.aliases
        if (aliases.isEmpty()) return
        val canonical = command.name
        for (alias in aliases) {
            if (alias.isBlank()) continue
            if (aliasesToName.remove(alias, canonical)) continue
            for (entry in aliasesToName.entries) {
                if (entry.key.equals(alias, ignoreCase = true) && entry.value == canonical) {
                    aliasesToName.remove(entry.key, canonical)
                    break
                }
            }
        }
    }

    private fun findCommandKey(name: String): String? {
        if (commandsByName.containsKey(name)) return name
        for (key in commandsByName.keys) {
            if (key.equals(name, ignoreCase = true)) return key
        }
        return null
    }

    private fun findAliasKey(alias: String): String? {
        if (aliasesToName.containsKey(alias)) return alias
        for (key in aliasesToName.keys) {
            if (key.equals(alias, ignoreCase = true)) return key
        }
        return null
    }

    private inner class DefaultRegistration(
        override val command: Command,
    ) : CommandRegistration {
        @Volatile
        private var unregistered = false

        override fun unregister() {
            if (unregistered) return
            unregistered = true
            unregister(command)
        }
    }

    companion object {
        private val BUILTIN_COMPLETER_KEYS = setOf("string", "boolean", "player", "world")

        private inline fun <T> withCommandClassLoader(classLoader: ClassLoader?, block: () -> T): T {
            if (classLoader == null) return block()
            val thread = Thread.currentThread()
            val previous = thread.contextClassLoader
            if (previous === classLoader) return block()
            thread.contextClassLoader = classLoader
            return try {
                block()
            } finally {
                thread.contextClassLoader = previous
            }
        }
    }
}
