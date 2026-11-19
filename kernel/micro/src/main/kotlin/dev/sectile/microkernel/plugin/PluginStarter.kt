package dev.sectile.microkernel.plugin

import dev.sectile.kernel.logger.Logger
import dev.sectile.kernel.plugin.model.PluginId
import dev.sectile.microkernel.config.KernelPluginConfig
import dev.sectile.microkernel.plugin.catalog.DefaultPluginCatalog
import dev.sectile.microkernel.plugin.graph.PluginDependencyGraph
import dev.sectile.microkernel.plugin.graph.PluginLoadGraphBuilder
import dev.sectile.microkernel.plugin.lifecycle.DefaultPluginLifecycleControl
import dev.sectile.microkernel.plugin.lifecycle.PluginStartupCancelledException
import java.util.concurrent.*

internal class PluginStarter(
    private val config: KernelPluginConfig,
    private val catalog: DefaultPluginCatalog,
    private val lifecycleControl: DefaultPluginLifecycleControl,
    private val logger: Logger,
) {
    fun start(pluginIds: Collection<PluginId>) {
        if (pluginIds.isEmpty()) {
            return
        }

        val graphBuilder = PluginLoadGraphBuilder()
        val graph = graphBuilder.build(pluginIds.mapNotNull { catalog.entry(it)?.metadata })

        if (config.loading.parallel) {
            startParallel(graph, graphBuilder)
            return
        }

        startSequence(graph)
    }

    private fun startSequence(graph: PluginDependencyGraph) {
        for (id in graph.topologicalOrder()) {
            try {
                lifecycleControl.start(id)
            } catch (_: PluginStartupCancelledException) {
                // Cancellation is controlled by PluginStarter/DefaultPluginLifecycleControl.
                // Do not print a full error stack for expected startup timeouts.
            } catch (error: Throwable) {
                logger.error("Plugin $id could not be started.", error)
            }
        }
    }

    private fun startParallel(graph: PluginDependencyGraph, graphBuilder: PluginLoadGraphBuilder) {
        val components = graph.connectedComponents()
        if (components.isEmpty()) {
            return
        }

        // Run parallel plugin loading asynchronously so server startup does not wait on plugins;
        // this coordinator virtual thread enforces the timeout and cleans up the executor on exit.
        Thread.ofVirtual().name("sectile-plugin-starter").start {
            val executor = Executors.newVirtualThreadPerTaskExecutor()
            val timeoutInMillis = config.loading.timeOut.inWholeMilliseconds
            val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutInMillis)

            try {
                val futures = if (components.size == 1) {
                    val only = components.first()
                    mapOf(only to executor.submit { startSequence(graph) })
                } else {
                    components.associateWith { component ->
                        executor.submit {
                            val componentGraph = graphBuilder.build(
                                component.mapNotNull { catalog.entry(it)?.metadata },
                            )
                            startSequence(componentGraph)
                        }
                    }
                }

                for ((component, future) in futures) {
                    val remainingNanos = deadline - System.nanoTime()
                    if (remainingNanos <= 0L) {
                        cancelComponent(component, future, timeoutInMillis)
                        continue
                    }

                    try {
                        future.get(remainingNanos, TimeUnit.NANOSECONDS)
                    } catch (_: TimeoutException) {
                        cancelComponent(component, future, timeoutInMillis)
                    } catch (_: CancellationException) {
                        // Already canceled by timeout or by another controlled path.
                    } catch (error: ExecutionException) {
                        val cause = error.cause
                        if (cause !is PluginStartupCancelledException) {
                            logger.error("A plugin loading group $component failed.", cause ?: error)
                        }
                    } catch (error: Exception) {
                        logger.error("A plugin loading group $component failed.", error)
                    }
                }
            } finally {
                executor.shutdownNow()
            }
        }
    }

    private fun cancelComponent(
        component: Set<PluginId>,
        future: Future<*>,
        timeoutInMillis: Long,
    ) {
        for (pluginId in component) {
            runCatching {
                lifecycleControl.cancelStartup(
                    pluginId,
                    "Plugin startup exceeded ${timeoutInMillis}ms.",
                )
            }.onFailure { error ->
                logger.error("Could not request cancellation for plugin $pluginId after startup timeout.", error)
            }
        }

        future.cancel(true)

        logger.warn(
            "Plugin loading group $component took more than ${timeoutInMillis}ms. " +
                "It was cancelled and startup will continue.",
        )
    }
}
