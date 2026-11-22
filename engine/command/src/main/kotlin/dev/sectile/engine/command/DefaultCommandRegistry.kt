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
import dev.sectile.network.api.NetworkConnector
import dev.sectile.network.protocol.command.outbound.ArgCacheInvalidationPacket
import java.util.Locale
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

    override val commands: Collection<Command>
        get() = commandsByName.values.toList()

    override fun register(command: Command): CommandRegistration {
        val name = command.name.lowercase(Locale.ROOT)
        require(name.isNotBlank()) { "Registered command must have a non-blank name." }

        synchronized(registrationLock) {
            val previous = commandsByName.put(name, command)
            if (previous != null) {
                removeAliasesFor(previous)
                unregisterCommandCompleters(previous)
            }
            for (alias in command.aliases) {
                aliasesToName[alias.lowercase(Locale.ROOT)] = name
            }
            registerCommandCompleters(command)
        }

        syncCommandTreeToAll()
        return DefaultRegistration(command)
    }

    override fun unregister(name: String): Boolean {
        val key = name.lowercase(Locale.ROOT)
        val removed = synchronized(registrationLock) {
            val command = commandsByName.remove(key) ?: return false
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
            val removedSuccess = commandsByName.remove(command.name.lowercase(Locale.ROOT), command)
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
        val key = nameOrAlias.lowercase(Locale.ROOT)
        commandsByName[key]?.let { return it }
        val canonical = aliasesToName[key] ?: return null
        return commandsByName[canonical]
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
        if (connections.size > 1 && NetworkConnector.isInitialized()) {
            NetworkConnector.packetsSender.sendPacket(connections, packet)
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

        val commandName = tokens[0].lowercase(Locale.ROOT)
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

    private fun registerCommandCompleters(command: Command) {
        val keys = LinkedHashSet<String>()
        registerCommandCompleters(command, command.name, command.javaClass.classLoader, keys)
        if (keys.isNotEmpty()) {
            keysByCommand[command] = keys
        }
    }

    private fun registerCommandCompleters(
        command: Command,
        path: String,
        classLoader: ClassLoader?,
        registeredKeys: MutableSet<String>,
    ) {
        command.args.forEachIndexed { idx, arg ->
            val completer = arg.completer ?: return@forEachIndexed
            val key = CommandTreeBuilder.argTypeIdFor(path, idx, arg)
            if (key in BUILTIN_COMPLETER_KEYS) return@forEachIndexed
            completersById.putIfAbsent(key) {
                withCommandClassLoader(classLoader) { completer() }
            }
            registeredKeys.add(key)
        }
        command.subcommands.values.forEach { sub ->
            registerCommandCompleters(sub, "$path/${sub.name}", classLoader, registeredKeys)
        }
    }

    private fun unregisterCommandCompleters(command: Command) {
        keysByCommand.remove(command)?.forEach { completersById.remove(it) }
    }

    private fun removeAliasesFor(command: Command) {
        val canonical = command.name.lowercase(Locale.ROOT)
        for (alias in command.aliases) {
            aliasesToName.remove(alias.lowercase(Locale.ROOT), canonical)
        }
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
