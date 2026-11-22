package dev.sectile.microkernel.plugin.scope.provider

import dev.sectile.kernel.logger.Logger
import dev.sectile.kernel.logger.LoggerSpy
import dev.sectile.kernel.plugin.scope.provider.CloseableDependencyProvider
import dev.sectile.kernel.plugin.scope.provider.ProtectedDependencyProvider
import dev.sectile.microkernel.plugin.scope.KernelPluginScope
import dev.sectile.microkernel.plugin.scope.KernelPluginScopeFactory
import java.util.Collections
import java.util.concurrent.CopyOnWriteArraySet

object ScopedLoggerSpy : LoggerSpy {
    private val loggers = CopyOnWriteArraySet<Logger>()

    override fun register(logger: Logger) {
        val scope = KernelPluginScopeFactory.acquire(logger)

        if (loggers.add(logger) && scope is KernelPluginScope) {
            val currentLogger = scope.getProvider(LoggerSpy::class.java)

            if (currentLogger is MultipleLoggerSpyHolderProvider) {
                currentLogger.loggers.add(logger)
            } else {
                val holder = MultipleLoggerSpyHolderProvider(this)
                holder.loggers.add(logger)
                scope.put(holder)
            }
        }
    }

    override fun unregister() {
        val scope = KernelPluginScopeFactory.acquire()
        val currentLogger = scope.getProvider(LoggerSpy::class.java)
        if (currentLogger is MultipleLoggerSpyHolderProvider) {
            currentLogger.close()
        }
    }

    fun getLoggers(): Set<Logger> = loggers

    private class MultipleLoggerSpyHolderProvider(
        private val spyScope: ScopedLoggerSpy,
        override val type: Class<LoggerSpy> = LoggerSpy::class.java,
    ) : CloseableDependencyProvider<LoggerSpy>,
        ProtectedDependencyProvider {

        val loggers: MutableSet<Logger> = Collections.synchronizedSet(mutableSetOf())

        override fun provide(): LoggerSpy = spyScope

        override fun close() {
            val snapshot = synchronized(loggers) {
                loggers.toList().also { loggers.clear() }
            }
            spyScope.loggers.removeAll(snapshot.toSet())
        }
    }
}
