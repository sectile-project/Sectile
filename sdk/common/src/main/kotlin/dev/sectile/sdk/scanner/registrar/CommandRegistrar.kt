package dev.sectile.sdk.scanner.registrar

import dev.sectile.engine.api.command.Command
import dev.sectile.kernel.Kernel
import dev.sectile.sdk.extension.commands

object CommandRegistrar : ObjectRegistrar<Command> {

    override val targetType: Class<Command> = Command::class.java

    override fun registrar(instance: Command) {
        val scope = Kernel.pluginScopeFactory.acquire(instance)
        scope.commands().register(instance)
    }
}
