package dev.sectile.microkernel.scheduler

import dev.sectile.kernel.scheduler.SchedulerProvider
import dev.sectile.kernel.scheduler.tick.TickDrivenScheduler
import dev.sectile.kernel.scheduler.tick.Ticks
import dev.sectile.microkernel.plugin.classloading.PluginClassLoader
import dev.sectile.microkernel.plugin.scope.KernelPluginScopeFactory
import dev.sectile.microkernel.scheduler.tick.TickScheduler
import kotlin.time.Duration

class KernelSchedulerProvider : SchedulerProvider {
    override fun createTickDriven(
        initialTick: Ticks,
        tickDuration: Duration,
    ): TickDrivenScheduler {
        val scheduler = TickScheduler(initialTick, tickDuration)
        if (Thread.currentThread().contextClassLoader is PluginClassLoader) {
            runCatching { KernelPluginScopeFactory.acquire() }
                .getOrNull()
                ?.onClose { scheduler.shutdown(cancelPendingTasks = true) }
        }
        return scheduler
    }
}
