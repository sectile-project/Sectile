package dev.sectile.microkernel.scheduler.realtime

import dev.sectile.kernel.Kernel
import dev.sectile.kernel.scheduler.Scheduler
import dev.sectile.kernel.scheduler.Task
import dev.sectile.kernel.scheduler.exception.SchedulerClosedException
import dev.sectile.microkernel.plugin.classloading.PluginClassLoader
import dev.sectile.microkernel.plugin.lifecycle.runtime.withPluginClassLoader
import dev.sectile.microkernel.scheduler.DefaultTask
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.max
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

internal class RealtimeScheduler(
    namePrefix: String = "sectile-scheduler",
    runOnVirtualThreads: Boolean = true,
    private val ownerClassLoader: PluginClassLoader? = Thread.currentThread().contextClassLoader as? PluginClassLoader,
) : Scheduler {

    override val minPrecisionUnit: Duration = 1.milliseconds

    private val startedAtNanos = System.nanoTime()
    private val stateLock = Any()

    @Volatile
    private var acceptingTasks = true

    private val nextTaskId = AtomicInteger(0)
    private val handles = ConcurrentHashMap<Int, TaskHandle>()
    private val scopeHandle = AtomicReference<AutoCloseable?>()

    internal fun attachScopeHandle(handle: AutoCloseable?) {
        if (handle != null && !scopeHandle.compareAndSet(null, handle)) {
            handle.close()
        }
    }

    private val timerExecutor = ScheduledThreadPoolExecutor(
        1,
        Thread.ofPlatform().daemon(true).name("$namePrefix-timer-", 0).factory(),
    ).apply {
        removeOnCancelPolicy = true
        executeExistingDelayedTasksAfterShutdownPolicy = false
        continueExistingPeriodicTasksAfterShutdownPolicy = false
    }

    private val workerExecutor: ExecutorService? =
        if (runOnVirtualThreads) {
            Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual().name("$namePrefix-worker-", 0).factory(),
            )
        } else {
            null
        }

    override fun runNow(task: () -> Unit) {
        val wrapped = wrapTask(task)
        synchronized(stateLock) {
            ensureAcceptingTasks()
            dispatch(wrapped)
        }
    }

    override fun runAt(
        task: () -> Unit,
        timeToExecute: Duration,
    ) {
        require(timeToExecute >= Duration.ZERO) { "timeToExecute cannot be negative: $timeToExecute" }
        val elapsedNanos = System.nanoTime() - startedAtNanos
        val remainingMillis = max(0L, (timeToExecute.inWholeNanoseconds - elapsedNanos) / 1_000_000L)
        runAfterDelay(task, remainingMillis.milliseconds)
    }

    override fun runAfterDelay(
        task: () -> Unit,
        delay: Duration,
    ): Task {
        require(delay >= Duration.ZERO) { "delay cannot be negative: $delay" }
        val wrapped = wrapTask(task)
        val id = nextId()
        val handle = TaskHandle()
        synchronized(stateLock) {
            ensureAcceptingTasks()
            handles[id] = handle
            val future = timerExecutor.schedule(
                {
                    if (handle.cancelled.get()) {
                        handles.remove(id, handle)
                        return@schedule
                    }
                    if (workerExecutor == null) {
                        handles.remove(id, handle)
                        executeSafely(wrapped)
                    } else {
                        dispatch {
                            try {
                                if (!handle.cancelled.get()) {
                                    executeSafely(wrapped)
                                }
                            } finally {
                                handles.remove(id, handle)
                            }
                        }
                    }
                },
                delay.inWholeMilliseconds,
                TimeUnit.MILLISECONDS,
            )
            handle.setFuture(future)
        }
        return DefaultTask(id, false, this)
    }

    override fun scheduleEvery(
        task: () -> Unit,
        startTime: Duration,
        interval: Duration,
    ): Task {
        require(startTime >= Duration.ZERO) { "startTime cannot be negative: $startTime" }
        require(interval > Duration.ZERO) { "interval must be greater than zero: $interval" }
        val wrapped = wrapTask(task)
        val id = nextId()
        val handle = TaskHandle()
        val elapsedMillis = max(0L, (System.nanoTime() - startedAtNanos) / 1_000_000L)
        val initialDelayMillis = max(0L, startTime.inWholeMilliseconds - elapsedMillis)
        synchronized(stateLock) {
            ensureAcceptingTasks()
            handles[id] = handle
            val future = timerExecutor.scheduleAtFixedRate(
                {
                    if (!handle.cancelled.get() && acceptingTasks) {
                        dispatch { executeSafely(wrapped) }
                    }
                },
                initialDelayMillis,
                interval.inWholeMilliseconds,
                TimeUnit.MILLISECONDS,
            )
            handle.setFuture(future)
        }
        return DefaultTask(id, false, this)
    }

    override fun scheduleWithDelay(
        task: () -> Unit,
        initialDelay: Duration,
        delayBetweenExecute: Duration,
    ): Task {
        require(initialDelay >= Duration.ZERO) { "initialDelay cannot be negative: $initialDelay" }
        require(delayBetweenExecute > Duration.ZERO) { "delayBetweenExecute must be greater than zero: $delayBetweenExecute" }
        val wrapped = wrapTask(task)
        val id = nextId()
        val handle = TaskHandle()
        synchronized(stateLock) {
            ensureAcceptingTasks()
            handles[id] = handle
            if (workerExecutor == null) {
                val future = timerExecutor.scheduleWithFixedDelay(
                    {
                        if (!handle.cancelled.get() && acceptingTasks) {
                            executeSafely(wrapped)
                        }
                    },
                    initialDelay.inWholeMilliseconds,
                    delayBetweenExecute.inWholeMilliseconds,
                    TimeUnit.MILLISECONDS,
                )
                handle.setFuture(future)
            } else {
                scheduleNextFixedDelay(id, handle, wrapped, initialDelay.inWholeMilliseconds, delayBetweenExecute.inWholeMilliseconds)
            }
        }
        return DefaultTask(id, false, this)
    }

    override fun cancelTask(taskId: Int): Boolean {
        val handle = handles.remove(taskId) ?: return false
        handle.cancel()
        return true
    }

    override fun shutdown(cancelPendingTasks: Boolean) {
        scopeHandle.getAndSet(null)?.close()
        synchronized(stateLock) {
            if (!acceptingTasks && timerExecutor.isShutdown) {
                return
            }
            acceptingTasks = false
            if (cancelPendingTasks) {
                val snapshot = handles.values.toList()
                handles.clear()
                snapshot.forEach { it.cancel() }
                timerExecutor.shutdownNow()
                workerExecutor?.shutdownNow()
            } else {
                timerExecutor.shutdown()
                workerExecutor?.shutdown()
            }
        }
    }

    private fun scheduleNextFixedDelay(
        id: Int,
        handle: TaskHandle,
        wrapped: () -> Unit,
        delayMillis: Long,
        repeatDelayMillis: Long,
    ) {
        synchronized(stateLock) {
            if (!acceptingTasks || handle.cancelled.get() || timerExecutor.isShutdown) {
                handles.remove(id, handle)
                return
            }
            val future = timerExecutor.schedule(
                {
                    if (handle.cancelled.get() || !acceptingTasks) {
                        handles.remove(id, handle)
                        return@schedule
                    }
                    dispatch {
                        try {
                            if (!handle.cancelled.get() && acceptingTasks) {
                                executeSafely(wrapped)
                            }
                        } finally {
                            scheduleNextFixedDelay(id, handle, wrapped, repeatDelayMillis, repeatDelayMillis)
                        }
                    }
                },
                delayMillis,
                TimeUnit.MILLISECONDS,
            )
            handle.setFuture(future)
        }
    }

    private fun dispatch(action: () -> Unit) {
        synchronized(stateLock) {
            if (!acceptingTasks) return
            val worker = workerExecutor
            if (worker != null) {
                worker.execute(action)
            } else {
                timerExecutor.execute(action)
            }
        }
    }

    private fun wrapTask(task: () -> Unit): () -> Unit {
        val loader = (task.javaClass.classLoader as? PluginClassLoader) ?: ownerClassLoader ?: return task
        return { withPluginClassLoader(loader, task) }
    }

    private fun executeSafely(task: () -> Unit) {
        try {
            task()
        } catch (error: Throwable) {
            if (error is InterruptedException && !acceptingTasks) {
                Thread.currentThread().interrupt()
                return
            }
            if (Kernel.isInitialized()) {
                Kernel.globalLogger.error("Scheduled task execution failed", error)
            } else {
                error.printStackTrace()
            }
        }
    }

    private fun ensureAcceptingTasks() {
        if (!acceptingTasks) {
            throw SchedulerClosedException()
        }
    }

    private fun nextId(): Int {
        var id: Int
        do {
            id = nextTaskId.incrementAndGet()
        } while (id == 0)
        return id
    }

    private class TaskHandle {
        val cancelled = AtomicBoolean(false)
        private var currentFuture: ScheduledFuture<*>? = null

        @Synchronized
        fun setFuture(future: ScheduledFuture<*>) {
            currentFuture = future
            if (cancelled.get()) {
                future.cancel(true)
            }
        }

        @Synchronized
        fun cancel() {
            cancelled.set(true)
            currentFuture?.cancel(true)
        }
    }
}
