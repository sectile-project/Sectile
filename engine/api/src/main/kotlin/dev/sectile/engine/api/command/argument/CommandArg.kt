package dev.sectile.engine.api.command.argument

import dev.sectile.engine.api.command.CommandSender

class TabContext(
    val sender: CommandSender,
    val partial: String,
)

data class CommandArg(
    val name: String,
    val description: String? = null,
    val typeId: String? = null,
    val completer: (TabContext.() -> Iterable<String>)? = null,
)

data class CommandFlag(
    val name: String,
    val aliases: Set<String> = emptySet(),
    val description: String? = null,
)
