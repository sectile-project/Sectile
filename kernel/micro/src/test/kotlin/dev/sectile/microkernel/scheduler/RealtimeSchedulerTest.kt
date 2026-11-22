package dev.sectile.microkernel.scheduler

import dev.sectile.kernel.plugin.model.PluginId
import dev.sectile.kernel.plugin.scope.provider.ProtectedLazyProvider
import dev.sectile.kernel.scheduler.Scheduler
import dev.sectile.kernel.scheduler.after
import dev.sectile.kernel.scheduler.every
import dev.sectile.kernel.scheduler.exception.SchedulerClosedException
import dev.sectile.microkernel.plugin.scope.KernelPluginScope
import dev.sectile.microkernel.plugin.scope.provider.ScopedExecutorProvider
import dev.sectile.microkernel.scheduler.realtime.RealtimeScheduler
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds

class RealtimeSchedulerTest {

    @Test
    fun `runNow and runAfterDelay execute and support cancellation`() {
        val scheduler = RealtimeScheduler("test-basic")
        try {
            val nowLatch = CountDownLatch(1)
            val isVirtual = AtomicBoolean(false)
            scheduler.runNow {
                isVirtual.set(Thread.currentThread().isVirtual)
                nowLatch.countDown()
            }
            assertTrue(nowLatch.await(2, TimeUnit.SECONDS))
            assertTrue(isVirtual.get())

            val delayedLatch = CountDownLatch(1)
            val cancelledRan = AtomicBoolean(false)

            val cancelledTask = scheduler.after(40.milliseconds) { cancelledRan.set(true) }
            cancelledTask.cancel()
            assertTrue(cancelledTask.canceled)

            scheduler.after(30.milliseconds) { delayedLatch.countDown() }
            assertTrue(delayedLatch.await(2, TimeUnit.SECONDS))

            Thread.sleep(60)
            assertFalse(cancelledRan.get())
        } finally {
            scheduler.shutdown(true)
        }
    }

    @Test
    fun `scheduleEvery and scheduleWithDelay repeat and stop on cancel`() {
        val scheduler = RealtimeScheduler("test-repeat")
        try {
            val everyCount = AtomicInteger(0)
            val everyLatch = CountDownLatch(2)
            val everyTask = scheduler.every(15.milliseconds) {
                everyCount.incrementAndGet()
                everyLatch.countDown()
            }
            assertTrue(everyLatch.await(2, TimeUnit.SECONDS))
            everyTask.cancel()

            val delayCount = AtomicInteger(0)
            val delayLatch = CountDownLatch(2)
            val delayTask = scheduler.scheduleWithDelay(
                task = {
                    delayCount.incrementAndGet()
                    delayLatch.countDown()
                },
                initialDelay = 10.milliseconds,
                delayBetweenExecute = 15.milliseconds,
            )
            assertTrue(delayLatch.await(2, TimeUnit.SECONDS))
            delayTask.cancel()

            Thread.sleep(30)
            val frozenEvery = everyCount.get()
            val frozenDelay = delayCount.get()

            Thread.sleep(60)
            assertEquals(frozenEvery, everyCount.get())
            assertEquals(frozenDelay, delayCount.get())
        } finally {
            scheduler.shutdown(true)
        }
    }

    @Test
    fun `shutdown stops scheduler and rejects new tasks`() {
        val scheduler = RealtimeScheduler("test-shutdown", runOnVirtualThreads = false)
        scheduler.shutdown(true)

        assertThrows(SchedulerClosedException::class.java) {
            scheduler.runNow {}
        }
        assertThrows(SchedulerClosedException::class.java) {
            scheduler.runAfterDelay({}, 10.milliseconds)
        }
    }

    @Test
    fun `closing kernel plugin scope shuts down scheduler and executor providers`() {
        val provider = KernelSchedulerProvider()
        val scope = KernelPluginScope(PluginId("scope-sched-test")).apply {
            put(ProtectedLazyProvider(Scheduler::class.java) { provider.createRealtime() })
            put(ScopedExecutorProvider { provider.createVirtualExecutor() })
        }

        val scheduler = requireNotNull(scope[Scheduler::class.java])
        val executor = requireNotNull(scope[ExecutorService::class.java])
        val workerPool = provider.createThreadPool(2, "pool").also { pool ->
            scope.onClose { pool.shutdownNow() }
        }

        val latch = CountDownLatch(2)
        executor.execute {
            if (Thread.currentThread().isVirtual) {
                latch.countDown()
            }
        }
        workerPool.execute {
            if (!Thread.currentThread().isVirtual) {
                latch.countDown()
            }
        }
        assertTrue(latch.await(2, TimeUnit.SECONDS))

        scope.close()

        assertTrue(executor.isShutdown)
        assertTrue(workerPool.isShutdown)
        assertThrows(SchedulerClosedException::class.java) {
            scheduler.runNow {}
        }
    }
}
