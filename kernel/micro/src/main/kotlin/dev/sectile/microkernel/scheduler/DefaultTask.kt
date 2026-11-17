package dev.sectile.microkernel.scheduler

import dev.sectile.kernel.scheduler.Scheduler
import dev.sectile.kernel.scheduler.Task

class DefaultTask(
    override val id: Int,
    @Volatile
    override var canceled: Boolean,
    @Volatile
    var scheduler: Scheduler?,
) : Task {
    override fun cancel() {
        if (!canceled) {
            scheduler?.cancelTask(id)
            scheduler = null
            canceled = true
        }
    }
}
