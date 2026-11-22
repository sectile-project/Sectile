package dev.sectile.engine.api.command

interface CommandRegistration : AutoCloseable {
    val command: Command

    fun unregister()

    override fun close() = unregister()
}

interface CommandRegistry : CommandDispatcher {
    fun register(command: Command): CommandRegistration

    fun register(vararg commands: Command): List<CommandRegistration> = commands.map(::register)

    fun unregister(name: String): Boolean

    fun unregister(command: Command): Boolean
}
