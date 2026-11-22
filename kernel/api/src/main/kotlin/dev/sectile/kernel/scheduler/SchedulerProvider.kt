package dev.sectile.kernel.scheduler

import dev.sectile.kernel.scheduler.tick.TickDrivenScheduler
import dev.sectile.kernel.scheduler.tick.Ticks
import java.util.concurrent.ExecutorService
import kotlin.time.Duration

interface SchedulerProvider {
    fun createTickDriven(
        initialTick: Ticks,
        tickDuration: Duration,
    ): TickDrivenScheduler

    fun createTickDriven(tickDuration: Duration): TickDrivenScheduler = createTickDriven(Ticks.ZERO, tickDuration)

    fun createRealtime(
        namePrefix: String = "sectile-scheduler",
        runOnVirtualThreads: Boolean = true,
    ): Scheduler

    fun createVirtualExecutor(namePrefix: String = "sectile-virtual"): ExecutorService

    fun createThreadPool(
        threads: Int,
        namePrefix: String = "sectile-worker",
    ): ExecutorService
}
