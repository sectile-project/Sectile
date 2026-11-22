package dev.sectile.kernel

import dev.sectile.kernel.event.EventDispatcher
import dev.sectile.kernel.logger.Logger
import dev.sectile.kernel.logger.LoggerFactory
import dev.sectile.kernel.logger.LoggerSpy
import dev.sectile.kernel.plugin.control.PluginLifecycleControl
import dev.sectile.kernel.plugin.query.PluginCatalog
import dev.sectile.kernel.plugin.scope.PluginScopeFactory
import dev.sectile.kernel.scheduler.SchedulerProvider
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

class Kernel private constructor(
    val bus: EventDispatcher,
    val globalLogger: Logger,
    val loggerFactory: LoggerFactory,
    val loggerSpy: LoggerSpy,
    val schedulerProvider: SchedulerProvider,
    val pluginCatalog: PluginCatalog,
    val pluginLifecycleControl: PluginLifecycleControl,
    val pluginScopeFactory: PluginScopeFactory,
    internal val basePluginDirectory: Path,
    internal val baseLibraryDirectory: Path,
) {
    companion object {
        @Volatile
        private var ref: Kernel? = null

        private val stopTasks = CopyOnWriteArrayList<() -> Unit>()
        private val stopTaskHandles = ConcurrentHashMap<() -> Unit, AutoCloseable>()
        private val shuttingDown = AtomicBoolean(false)

        val bus: EventDispatcher get() = instance.bus

        val globalLogger: Logger get() = instance.globalLogger
        val loggerFactory: LoggerFactory get() = instance.loggerFactory
        val loggerSpy: LoggerSpy get() = instance.loggerSpy

        val schedulerProvider: SchedulerProvider get() = instance.schedulerProvider

        val pluginCatalog: PluginCatalog get() = instance.pluginCatalog
        val pluginLifecycleControl: PluginLifecycleControl get() = instance.pluginLifecycleControl
        val pluginScopeFactory: PluginScopeFactory get() = instance.pluginScopeFactory

        val pluginDirectory: Path get() = instance.basePluginDirectory
        val libraryDirectory: Path get() = instance.baseLibraryDirectory

        fun init(
            eventBus: EventDispatcher,
            globalLogger: Logger,
            loggerFactory: LoggerFactory,
            loggerSpy: LoggerSpy,
            schedulerProvider: SchedulerProvider,
            pluginCatalog: PluginCatalog,
            pluginLifecycleControl: PluginLifecycleControl,
            pluginScopeFactory: PluginScopeFactory,
            basePluginDirectory: Path,
            baseLibraryDirectory: Path,
        ) {
            check(ref == null) { "Kernel already initialized." }

            ref =
                Kernel(
                    eventBus,
                    globalLogger,
                    loggerFactory,
                    loggerSpy,
                    schedulerProvider,
                    pluginCatalog,
                    pluginLifecycleControl,
                    pluginScopeFactory,
                    basePluginDirectory,
                    baseLibraryDirectory,
                )

            Runtime.getRuntime().addShutdownHook(
                Thread {
                    shuttingDown.set(true)
                    stopTasks.asReversed().forEach { task ->
                        runCatching(task).onFailure { error ->
                            System.err.println("Shutdown task failed.")
                            error.printStackTrace()
                        }
                    }
                },
            )
        }

        fun addStopTask(task: () -> Unit) {
            check(!shuttingDown.get()) { "Cannot register stop task while shutting down." }
            stopTasks += task
            if (ref != null) {
                runCatching { pluginScopeFactory.acquire(task) }
                    .getOrNull()
                    ?.onClose { removeStopTask(task) }
                    ?.let { handle -> stopTaskHandles.put(task, handle)?.close() }
            }
        }

        fun removeStopTask(task: () -> Unit) {
            stopTasks.remove(task)
            stopTaskHandles.remove(task)?.close()
        }

        val instance: Kernel
            get() = ref ?: error("Kernel not initialized.")

        fun isInitialized(): Boolean = ref != null
    }
}
