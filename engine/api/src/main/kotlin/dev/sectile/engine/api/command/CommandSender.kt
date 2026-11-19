package dev.sectile.engine.api.command

import dev.sectile.engine.api.MessageReceiver
import java.util.UUID

interface CommandSender : MessageReceiver {
    val name: String
    val uuid: UUID

    fun hasPermission(permission: String): Boolean = true
}

interface ConsoleCommandSender : CommandSender
