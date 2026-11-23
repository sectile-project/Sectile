package dev.sectile.engine.api.command

import dev.sectile.engine.api.player.Player

interface CommandDispatcher {
    val all: Collection<Command>

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
