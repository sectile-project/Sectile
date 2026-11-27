package dev.sectile.engine.command.scope

import dev.sectile.engine.api.command.Command
import dev.sectile.engine.api.command.CommandRegistration
import dev.sectile.engine.api.command.CommandRegistry
import dev.sectile.engine.api.command.CommandSender
import dev.sectile.engine.api.player.Player
import dev.sectile.kernel.plugin.scope.provider.CloseableDependencyProvider
import dev.sectile.kernel.plugin.scope.provider.ProtectedDependencyProvider
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean

class ScopedCommandRegistryProvider(
    private val delegate: CommandRegistry,
    override val type: Class<CommandRegistry> = CommandRegistry::class.java,
) : CommandRegistry,
    CloseableDependencyProvider<CommandRegistry>,
    ProtectedDependencyProvider {

    private val registrations: MutableList<CommandRegistration> =
        Collections.synchronizedList(mutableListOf())

    override val all: Collection<Command>
        get() = delegate.all

    override fun register(command: Command): CommandRegistration {
        val wrapped = ScopedRegistration(delegate.register(command))
        registrations.add(wrapped)
        return wrapped
    }

    override fun register(vararg commands: Command): List<CommandRegistration> = delegate.register(*commands).map { ScopedRegistration(it) }.also { registrations.addAll(it) }

    override fun unregister(name: String): Boolean {
        val target = synchronized(registrations) {
            registrations.firstOrNull { it.command.name.equals(name, ignoreCase = true) }
                ?.also { registrations.remove(it) }
        } ?: return false
        target.unregister()
        return true
    }

    override fun unregister(command: Command): Boolean {
        val target = synchronized(registrations) {
            registrations.firstOrNull { it.command == command }
                ?.also { registrations.remove(it) }
        } ?: return false
        target.unregister()
        return true
    }

    override fun resolve(nameOrAlias: String): Command? = delegate.resolve(nameOrAlias)

    override fun completeArgType(
        sender: CommandSender,
        argTypeId: String,
        partial: String,
    ): List<String> = delegate.completeArgType(sender, argTypeId, partial)

    override fun invalidateArgCache(vararg argTypeIds: String) = delegate.invalidateArgCache(*argTypeIds)

    override fun syncCommandTree(player: Player) = delegate.syncCommandTree(player)

    override fun syncCommandTreeToAll() = delegate.syncCommandTreeToAll()

    override fun execute(sender: CommandSender, commandLine: String): Boolean = delegate.execute(sender, commandLine)

    override fun suggest(
        sender: CommandSender,
        commandLine: String,
        cursorIndex: Int,
    ): List<String> = delegate.suggest(sender, commandLine, cursorIndex)

    override fun suggestTokens(
        sender: CommandSender,
        words: List<String>,
        wordIndex: Int,
    ): List<String> = delegate.suggestTokens(sender, words, wordIndex)

    override fun provide(): CommandRegistry = this

    override fun close() {
        val snapshot = synchronized(registrations) {
            registrations.toList().also { registrations.clear() }
        }
        snapshot.forEach { it.unregister() }
    }

    private inner class ScopedRegistration(
        private val raw: CommandRegistration,
    ) : CommandRegistration {
        private val unregistered = AtomicBoolean(false)

        override val command: Command
            get() = raw.command

        override fun unregister() {
            synchronized(registrations) {
                registrations.remove(this)
            }
            if (unregistered.compareAndSet(false, true)) {
                raw.unregister()
            }
        }
    }
}
