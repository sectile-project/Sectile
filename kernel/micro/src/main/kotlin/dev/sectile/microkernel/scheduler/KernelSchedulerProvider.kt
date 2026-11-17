package dev.sectile.microkernel.scheduler

import dev.sectile.kernel.scheduler.SchedulerProvider
import dev.sectile.kernel.scheduler.tick.TickDrivenScheduler
import dev.sectile.kernel.scheduler.tick.Ticks
import dev.sectile.microkernel.scheduler.tick.TickScheduler
import kotlin.time.Duration

class KernelSchedulerProvider : SchedulerProvider {
    override fun createTickDriven(
        initialTick: Ticks,
        tickDuration: Duration,
    ): TickDrivenScheduler = TickScheduler(initialTick, tickDuration)
}
