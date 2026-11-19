package dev.sectile.engine.api.command

import dev.sectile.engine.api.player.Player

interface CommandRegistration : AutoCloseable {
    val command: Command

    fun unregister()

    override fun close() = unregister()
}

interface CommandRegistry {
    val commands: Collection<Command>

    fun register(command: Command): CommandRegistration

    fun register(vararg commands: Command): List<CommandRegistration> = commands.map(::register)

    fun unregister(name: String): Boolean

    fun unregister(command: Command): Boolean

    fun resolve(nameOrAlias: String): Command?

    fun completeArgType(sender: CommandSender, argTypeId: String, partial: String): List<String>

    fun invalidateArgCache(vararg argTypeIds: String)

    fun syncCommandTree(player: Player)

    fun syncCommandTreeToAll()

    fun execute(sender: CommandSender, commandLine: String): Boolean

    fun suggest(
        sender: CommandSender,
        commandLine: String,
        cursorIndex: Int = commandLine.length,
    ): List<String>

    fun suggestTokens(
        sender: CommandSender,
        words: List<String>,
        wordIndex: Int,
    ): List<String>
}
