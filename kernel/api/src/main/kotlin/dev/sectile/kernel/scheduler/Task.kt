package dev.sectile.kernel.scheduler

interface Task {
    val id: Int
    val canceled: Boolean

    fun cancel()
}
