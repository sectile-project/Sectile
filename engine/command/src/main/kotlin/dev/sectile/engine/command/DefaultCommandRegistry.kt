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
import dev.sectile.network.protocol.interfaceui.ArgCacheInvalidationPacket
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

class DefaultCommandRegistry(
    private val logger: Logger = Kernel.globalLogger,
) : CommandRegistry {

    private val commandsByName = ConcurrentHashMap<String, Command>()
    private val aliasesToName = ConcurrentHashMap<String, String>()
    private val completersById = ConcurrentHashMap<String, TabContext.() -> Iterable<String>>()
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
            commandsByName[name] = command
            for (alias in command.aliases) {
                aliasesToName[alias.lowercase(Locale.ROOT)] = name
            }
            registerCommandCompleters(command, command.name)
        }

        syncCommandTreeToAll()
        return DefaultRegistration(command)
    }

    override fun unregister(name: String): Boolean {
        val key = name.lowercase(Locale.ROOT)
        val removed = synchronized(registrationLock) {
            val command = commandsByName.remove(key) ?: return false
            removeAliasesFor(command)
            unregisterCommandCompleters(command, command.name)
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
                unregisterCommandCompleters(command, command.name)
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
            CommandParser.parseAndExecute(sender, command, tokens)
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

    private fun registerCommandCompleters(command: Command, path: String) {
        command.args.forEachIndexed { idx, arg ->
            val completer = arg.completer ?: return@forEachIndexed
            completersById.putIfAbsent(CommandTreeBuilder.argTypeIdFor(path, idx, arg), completer)
        }
        command.subcommands.values.forEach { sub ->
            registerCommandCompleters(sub, "$path/${sub.name}")
        }
    }

    private fun unregisterCommandCompleters(command: Command, path: String) {
        command.args.forEachIndexed { idx, arg ->
            if (arg.typeId == null && arg.completer != null) {
                completersById.remove(CommandTreeBuilder.argTypeIdFor(path, idx, arg))
            }
        }
        command.subcommands.values.forEach { sub ->
            unregisterCommandCompleters(sub, "$path/${sub.name}")
        }
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
}
