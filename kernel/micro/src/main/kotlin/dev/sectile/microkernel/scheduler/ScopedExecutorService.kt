package dev.sectile.microkernel.scheduler

import dev.sectile.microkernel.plugin.classloading.PluginClassLoader
import dev.sectile.microkernel.plugin.lifecycle.runtime.withPluginClassLoader
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

internal class ScopedExecutorService(
    private val delegate: ExecutorService,
    private val ownerClassLoader: PluginClassLoader?,
) : ExecutorService {

    private val scopeHandle = AtomicReference<AutoCloseable?>()

    internal fun attachScopeHandle(handle: AutoCloseable?) {
        if (handle != null && !scopeHandle.compareAndSet(null, handle)) {
            handle.close()
        }
    }

    private fun releaseScopeHandle() {
        scopeHandle.getAndSet(null)?.close()
    }

    override fun execute(command: Runnable) {
        delegate.execute(wrap(command))
    }

    override fun shutdown() {
        releaseScopeHandle()
        delegate.shutdown()
    }

    override fun shutdownNow(): List<Runnable> {
        releaseScopeHandle()
        return delegate.shutdownNow()
    }

    override fun isShutdown(): Boolean = delegate.isShutdown

    override fun isTerminated(): Boolean = delegate.isTerminated

    override fun awaitTermination(timeout: Long, unit: TimeUnit): Boolean = delegate.awaitTermination(timeout, unit)

    override fun <T : Any?> submit(task: Callable<T>): Future<T> = delegate.submit(wrap(task))

    override fun <T : Any?> submit(task: Runnable, result: T): Future<T> = delegate.submit(wrap(task), result)

    override fun submit(task: Runnable): Future<*> = delegate.submit(wrap(task))

    override fun <T : Any?> invokeAll(tasks: Collection<Callable<T>>): List<Future<T>> = delegate.invokeAll(tasks.map(::wrap))

    override fun <T : Any?> invokeAll(
        tasks: Collection<Callable<T>>,
        timeout: Long,
        unit: TimeUnit,
    ): List<Future<T>> = delegate.invokeAll(tasks.map(::wrap), timeout, unit)

    override fun <T : Any?> invokeAny(tasks: Collection<Callable<T>>): T = delegate.invokeAny(tasks.map(::wrap))

    override fun <T : Any?> invokeAny(
        tasks: Collection<Callable<T>>,
        timeout: Long,
        unit: TimeUnit,
    ): T = delegate.invokeAny(tasks.map(::wrap), timeout, unit)

    private fun wrap(task: Runnable): Runnable {
        val loader = resolveLoader(task) ?: return task
        return Runnable {
            withPluginClassLoader(loader) {
                task.run()
            }
        }
    }

    private fun <T> wrap(task: Callable<T>): Callable<T> {
        val loader = resolveLoader(task) ?: return task
        return Callable {
            withPluginClassLoader(loader) {
                task.call()
            }
        }
    }

    private fun resolveLoader(task: Any): PluginClassLoader? = (task.javaClass.classLoader as? PluginClassLoader) ?: ownerClassLoader
}
