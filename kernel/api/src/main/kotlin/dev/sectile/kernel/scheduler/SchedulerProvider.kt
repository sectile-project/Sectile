package dev.sectile.kernel.scheduler

import dev.sectile.kernel.scheduler.tick.TickDrivenScheduler
import dev.sectile.kernel.scheduler.tick.Ticks
import kotlin.time.Duration

interface SchedulerProvider {
    fun createTickDriven(
        initialTick: Ticks,
        tickDuration: Duration,
    ): TickDrivenScheduler

    fun createTickDriven(tickDuration: Duration): TickDrivenScheduler = createTickDriven(Ticks.ZERO, tickDuration)
}
