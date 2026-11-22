package dev.sectile.microkernel.plugin.lifecycle

import dev.sectile.kernel.logger.Logger
import dev.sectile.kernel.plugin.control.PluginLifecycleControl
import dev.sectile.kernel.plugin.exception.PluginDependencyException
import dev.sectile.kernel.plugin.exception.PluginNotFoundException
import dev.sectile.kernel.plugin.model.PluginId
import dev.sectile.kernel.plugin.model.dependency.DependencyKind
import dev.sectile.kernel.plugin.model.lifecycle.PluginState
import dev.sectile.microkernel.config.KernelPluginConfig
import dev.sectile.microkernel.plugin.PluginInstaller
import dev.sectile.microkernel.plugin.catalog.DefaultPluginCatalog
import dev.sectile.microkernel.plugin.catalog.PluginEntry
import dev.sectile.microkernel.plugin.graph.PluginLoadGraphBuilder
import dev.sectile.microkernel.plugin.library.PluginLibraryResolver
import dev.sectile.microkernel.plugin.lifecycle.logger.PluginStartupLogController
import dev.sectile.microkernel.plugin.lifecycle.runtime.PluginInstanceCreator
import dev.sectile.microkernel.plugin.lifecycle.runtime.PluginRuntimeReleaser
import dev.sectile.microkernel.plugin.scanner.PluginCandidate
import dev.sectile.microkernel.plugin.scanner.PluginScanner
import dev.sectile.microkernel.plugin.validation.PluginDependencyValidator

internal class DefaultPluginLifecycleControl(
    private val catalog: DefaultPluginCatalog,
    libraryResolver: PluginLibraryResolver,
    private val config: KernelPluginConfig,
    private val logger: Logger,
    parentClassLoader: ClassLoader,
    private val scanner: PluginScanner,
    private val installer: PluginInstaller,
) : PluginLifecycleControl {
    private val dependencyValidator = PluginDependencyValidator()
    private val reloadLock = Any()
    private val startupLogs = PluginStartupLogController(config, logger)
    private val releaser = PluginRuntimeReleaser(logger, startupLogs)
    private val instanceCreator = PluginInstanceCreator(catalog, libraryResolver, logger, parentClassLoader)
    private val runner = PluginLifecycleRunner(instanceCreator, releaser, startupLogs, logger)

    override fun load(id: PluginId) {
        loadWithDependencies(id, keepStartupLogger = false)
    }

    override fun enable(id: PluginId) {
        enableWithDependencies(id)
    }

    override fun disable(id: PluginId) {
        disableWithDependents(id, linkedSetOf())
    }

    override fun reload(id: PluginId) {
        synchronized(reloadLock) {
            val entry = requireEntry(id)
            val refreshed = scanLatestCandidate(id, entry)
            val disabled = mutableListOf<Pair<PluginId, PluginState>>()

            disableWithDependents(id, linkedSetOf(), disabled)
            catalog.updateCandidate(refreshed)

            loadWithDependencies(id, keepStartupLogger = true)
            enableWithDependencies(id)

            for ((dependentId, priorState) in disabled.asReversed()) {
                if (dependentId == id) continue
                if (priorState == PluginState.ENABLED) {
                    loadWithDependencies(dependentId, keepStartupLogger = true)
                    enableWithDependencies(dependentId)
                } else if (priorState == PluginState.LOADED) {
                    loadWithDependencies(dependentId, keepStartupLogger = false)
                }
            }
        }
    }

    override fun reloadAll() {
        synchronized(reloadLock) {
            val pluginsDir = config.paths.plugins.toFile()
            val candidates = scanner.scan(pluginsDir)
            installer.validateDuplicateIds(candidates)

            val candidatesById = candidates.associateBy { it.id }
            val selectedCandidates = candidates.filter { installer.isSelected(it) }
            dependencyValidator.validateInstalledPlugins(selectedCandidates.map { it.metadata })
            val selectedIds = selectedCandidates.mapTo(linkedSetOf()) { it.id }

            val previousEntries = catalog.entries()
            val previousStates = previousEntries.associate { it.id to it.state }
            val visited = linkedSetOf<PluginId>()
            val existingReverseOrder = runCatching {
                PluginLoadGraphBuilder()
                    .build(previousEntries.map { it.metadata })
                    .topologicalOrder()
                    .asReversed()
            }.getOrElse {
                previousEntries.map { it.id }.asReversed()
            }

            for (entry in previousEntries) {
                if (entry.id !in selectedIds) {
                    disableWithDependents(entry.id, visited)
                }
            }
            for (existingId in existingReverseOrder) {
                disableWithDependents(existingId, visited)
            }

            for (entry in previousEntries) {
                if (entry.id !in candidatesById) {
                    catalog.remove(entry.id)
                }
            }

            for (candidate in candidates) {
                val id = candidate.id
                if (catalog.entry(id) != null) {
                    val updated = catalog.updateCandidate(candidate)
                    if (id !in selectedIds) {
                        synchronized(updated) {
                            updated.state = PluginState.SKIPPED
                            updated.failure = null
                        }
                    }
                } else {
                    val state = if (id in selectedIds) PluginState.WAITING_FOR_DEPENDENCIES else PluginState.SKIPPED
                    catalog.register(candidate, state)
                }
            }

            val orderedSelectedIds =
                if (selectedCandidates.isEmpty()) {
                    emptyList()
                } else {
                    PluginLoadGraphBuilder()
                        .build(selectedCandidates.map { it.metadata })
                        .topologicalOrder()
                }

            var firstError: Throwable? = null
            for (id in orderedSelectedIds) {
                val priorState = previousStates[id]
                val shouldEnable =
                    priorState == null ||
                        priorState == PluginState.ENABLED ||
                        priorState == PluginState.WAITING_FOR_DEPENDENCIES ||
                        priorState == PluginState.SKIPPED
                val shouldLoadOnly = priorState == PluginState.LOADED

                try {
                    if (shouldEnable) {
                        loadWithDependencies(id, keepStartupLogger = true)
                        enableWithDependencies(id)
                    } else if (shouldLoadOnly) {
                        loadWithDependencies(id, keepStartupLogger = false)
                    }
                } catch (error: Throwable) {
                    logger.error("Could not reload plugin $id.", error)
                    if (firstError == null) {
                        firstError = error
                    }
                }
            }

            firstError?.let { throw it }
        }
    }

    internal fun start(id: PluginId) {
        val entry = requireEntry(id)

        try {
            loadWithDependencies(id, keepStartupLogger = true)
            enableWithDependencies(id)
        } catch (error: PluginStartupCancelledException) {
            throw error
        } catch (error: Throwable) {
            if (entry.state != PluginState.FAILED && entry.state != PluginState.CANCELLED) {
                runner.fail(entry, PluginState.FAILED, error)
            }
            throw error
        }
    }

    internal fun shutdown() {
        val entries =
            catalog
                .entries()
                .filter { it.state == PluginState.ENABLED || it.state == PluginState.LOADED }

        val orderedIds =
            runCatching {
                PluginLoadGraphBuilder()
                    .build(entries.map { it.metadata })
                    .topologicalOrder()
                    .asReversed()
            }.getOrElse {
                entries.map { it.id }.asReversed()
            }

        for (id in orderedIds) {
            runCatching { disable(id) }
                .onFailure { error -> logger.error("Could not disable plugin $id during shutdown.", error) }
        }
    }

    internal fun cancelStartup(
        id: PluginId,
        reason: String,
    ) {
        val entry = requireEntry(id)
        runner.requestCancel(
            entry = entry,
            phase = cancellationPhaseOf(entry),
            reason = reason,
        )
    }

    private fun loadWithDependencies(
        id: PluginId,
        keepStartupLogger: Boolean,
    ) {
        val entry = requireEntry(id)

        for ((pluginId) in entry.metadata.requiredDependencies) {
            val dependencyEntry = requireEntry(pluginId)
            if (dependencyEntry.state == PluginState.SKIPPED || dependencyEntry.state == PluginState.CANCELLED) {
                throw PluginDependencyException("Plugin $id requires unavailable dependency $pluginId.")
            }

            loadWithDependencies(pluginId, keepStartupLogger = false)
        }

        runner.load(entry, keepStartupLogger)
    }

    private fun enableWithDependencies(id: PluginId) {
        val entry = requireEntry(id)

        for (dependency in entry.metadata.requiredDependencies) {
            val dependencyEntry = requireEntry(dependency.id)
            if (dependencyEntry.state == PluginState.SKIPPED || dependencyEntry.state == PluginState.CANCELLED) {
                throw PluginDependencyException("Plugin $id requires unavailable dependency ${dependency.id}.")
            }

            enableWithDependencies(dependency.id)

            if (dependencyEntry.state != PluginState.ENABLED) {
                throw PluginDependencyException(
                    "Plugin $id requires dependency ${dependency.id} to be enabled, but it is ${dependencyEntry.state}.",
                )
            }
        }

        if (entry.state == PluginState.DISABLED || entry.state == PluginState.FAILED || entry.state == PluginState.CANCELLED) {
            runner.load(entry, keepStartupLogger = true)
        }

        if (entry.state == PluginState.WAITING_FOR_DEPENDENCIES || entry.state == PluginState.SKIPPED) {
            loadWithDependencies(id, keepStartupLogger = true)
        }

        runner.enable(entry)
    }

    private fun disableWithDependents(
        id: PluginId,
        visited: MutableSet<PluginId>,
        disabled: MutableList<Pair<PluginId, PluginState>>? = null,
    ) {
        if (!visited.add(id)) {
            return
        }

        val entry = requireEntry(id)
        val priorState = entry.state

        for (dependent in directDependentsOf(id)) {
            if (dependent.state == PluginState.ENABLED || dependent.state == PluginState.LOADED) {
                disableWithDependents(dependent.id, visited, disabled)
            }
        }

        runner.disable(entry)
        if (disabled != null && (priorState == PluginState.ENABLED || priorState == PluginState.LOADED)) {
            disabled.add(id to priorState)
        }
    }

    private fun directDependentsOf(id: PluginId): List<PluginEntry> {
        val targetLoader = catalog.entry(id)?.classLoader
        return catalog.entries().filter { entry ->
            if (entry.id == id) return@filter false
            val hasRequired = entry.metadata.dependencies.any { it.id == id && it.kind == DependencyKind.REQUIRED }
            val linkedByLoader = targetLoader != null && entry.classLoader?.dependsOn(targetLoader) == true
            hasRequired || linkedByLoader
        }
    }

    private fun scanLatestCandidate(id: PluginId, entry: PluginEntry): PluginCandidate {
        val currentJar = entry.candidate.jarFile
        if (currentJar.isFile) {
            val candidate = scanner.scanFile(currentJar)
            if (candidate.id == id) {
                return candidate
            }
        }
        return scanner.scan(config.paths.plugins.toFile()).firstOrNull { it.id == id }
            ?: throw PluginNotFoundException(id)
    }

    private fun cancellationPhaseOf(entry: PluginEntry): PluginState = synchronized(entry) {
        when (entry.state) {
            PluginState.ENABLING -> PluginState.ENABLING
            PluginState.LOADING -> PluginState.LOADING
            else -> PluginState.CANCELLED
        }
    }

    private fun requireEntry(id: PluginId): PluginEntry = catalog.entry(id) ?: throw PluginNotFoundException(id)
}
