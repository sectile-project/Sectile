package dev.sectile.plugin.debug.commands

import dev.sectile.engine.api.command.BaseCommand
import dev.sectile.engine.api.command.CommandSender
import dev.sectile.engine.api.command.argument.Arguments
import dev.sectile.engine.chat.color.Color
import dev.sectile.engine.chat.component.Message
import dev.sectile.engine.chat.component.receiveMessage
import dev.sectile.kernel.logger.logger

class DebugCommand :
    BaseCommand(
        name = "debug",
        description = "Debug plugin test command.",
        aliases = arrayOf("dbg"),
    ) {

    init {
        flag("shout", "s", description = "Uppercases the echoed message.")
    }

    override fun execute(sender: CommandSender, args: Arguments) {
        val text = args.nextString() ?: "Debug plugin is active (sender=${sender.name})"

        val formatted = if (args.hasFlag("shout")) text.uppercase() else text
        logger().info("executed by ${sender.name}: $formatted")

        Message.of("[Debug] $formatted").apply {
            color = Color.DARK_AQUA
            bold = true
            underline = true
        }.sendTo(sender)
    }
}
