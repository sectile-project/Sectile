package dev.sectile.kernel.scheduler

import kotlin.time.Duration

fun Scheduler.after(
    delay: Duration,
    task: () -> Unit,
) {
    this.runAfterDelay(task, delay)
}

fun Scheduler.at(
    time: Duration,
    task: () -> Unit,
) {
    this.runAt(task, time)
}

fun Scheduler.every(
    interval: Duration,
    task: () -> Unit,
): Task = this.scheduleEvery(task, interval)

fun Scheduler.every(
    interval: Duration,
    startingIn: Duration = Duration.ZERO,
    task: () -> Unit,
): Task = this.scheduleEvery(task, startingIn, interval)
