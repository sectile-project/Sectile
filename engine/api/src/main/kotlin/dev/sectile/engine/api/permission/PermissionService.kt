package dev.sectile.engine.api.permission

import dev.sectile.engine.api.command.CommandSender

interface PermissionService {

    fun hasPermission(sender: CommandSender, permission: String): Boolean
}
