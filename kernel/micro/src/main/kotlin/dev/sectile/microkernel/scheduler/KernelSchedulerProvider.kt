package dev.sectile.microkernel.scheduler

import dev.sectile.kernel.scheduler.Scheduler
import dev.sectile.kernel.scheduler.SchedulerProvider
import dev.sectile.kernel.scheduler.tick.TickDrivenScheduler
import dev.sectile.kernel.scheduler.tick.Ticks
import dev.sectile.microkernel.plugin.classloading.PluginClassLoader
import dev.sectile.microkernel.plugin.scope.KernelPluginScopeFactory
import dev.sectile.microkernel.scheduler.realtime.RealtimeScheduler
import dev.sectile.microkernel.scheduler.tick.TickScheduler
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.time.Duration

class KernelSchedulerProvider : SchedulerProvider {
    override fun createTickDriven(
        initialTick: Ticks,
        tickDuration: Duration,
    ): TickDrivenScheduler {
        val loader = currentPluginClassLoader()
        val scheduler = TickScheduler(initialTick, tickDuration)
        scheduler.attachScopeHandle(bindToScope(loader) { scheduler.shutdown(cancelPendingTasks = true) })
        return scheduler
    }

    override fun createRealtime(
        namePrefix: String,
        runOnVirtualThreads: Boolean,
    ): Scheduler = createRealtime(namePrefix, runOnVirtualThreads, bindToScope = true)

    internal fun createRealtime(
        namePrefix: String = "sectile-scheduler",
        runOnVirtualThreads: Boolean = true,
        bindToScope: Boolean,
    ): Scheduler {
        val loader = currentPluginClassLoader()
        val scheduler = RealtimeScheduler(
            namePrefix = prefixFor(namePrefix, loader),
            runOnVirtualThreads = runOnVirtualThreads,
            ownerClassLoader = loader,
        )
        if (bindToScope) {
            scheduler.attachScopeHandle(bindToScope(loader) { scheduler.shutdown(cancelPendingTasks = true) })
        }
        return scheduler
    }

    override fun createVirtualExecutor(namePrefix: String): ExecutorService = createVirtualExecutor(namePrefix, bindToScope = true)

    internal fun createVirtualExecutor(
        namePrefix: String = "sectile-virtual",
        bindToScope: Boolean,
    ): ExecutorService {
        val loader = currentPluginClassLoader()
        val factory = Thread.ofVirtual().name("${prefixFor(namePrefix, loader)}-", 0).factory()
        val executor = ScopedExecutorService(Executors.newThreadPerTaskExecutor(factory), loader)
        if (bindToScope) {
            executor.attachScopeHandle(bindToScope(loader) { executor.shutdownNow() })
        }
        return executor
    }

    override fun createThreadPool(
        threads: Int,
        namePrefix: String,
    ): ExecutorService {
        require(threads > 0) { "Thread pool size must be greater than zero: $threads" }
        val loader = currentPluginClassLoader()
        val factory = Thread.ofPlatform().daemon(true).name("${prefixFor(namePrefix, loader)}-", 0).factory()
        val executor = ScopedExecutorService(Executors.newFixedThreadPool(threads, factory), loader)
        executor.attachScopeHandle(bindToScope(loader) { executor.shutdownNow() })
        return executor
    }

    private fun currentPluginClassLoader(): PluginClassLoader? = Thread.currentThread().contextClassLoader as? PluginClassLoader

    private fun prefixFor(
        basePrefix: String,
        loader: PluginClassLoader?,
    ): String = if (loader != null) "plugin-${loader.id.value}-$basePrefix" else basePrefix

    private fun bindToScope(
        loader: PluginClassLoader?,
        onCloseAction: () -> Unit,
    ): AutoCloseable? {
        if (loader == null || loader.isClosed) return null
        return runCatching { KernelPluginScopeFactory.acquire(loader) }
            .getOrNull()
            ?.onClose(onCloseAction)
    }
}
