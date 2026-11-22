package dev.sectile.microkernel.plugin.classloading

import dev.sectile.kernel.plugin.model.PluginId
import dev.sectile.microkernel.Microkernel
import dev.sectile.microkernel.plugin.scope.KernelPluginScope
import dev.sectile.microkernel.plugin.scope.PluginsScopeContainer
import java.net.URL
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path

internal class PluginClassLoader(
    val id: PluginId,
    urls: Array<URL>,
    private val dependencyClassLoaders: List<ClassLoader>,
    parentClassLoader: ClassLoader,
    private val stagedJarPath: Path? = null,
) : URLClassLoader(id.toString(), urls, parentClassLoader) {
    companion object {
        init {
            registerAsParallelCapable()
        }
    }

    override fun close() {
        val scope = PluginsScopeContainer.removeScope(this)
        if (scope is KernelPluginScope) {
            runCatching {
                scope.close()
            }.onFailure {
                Microkernel.instance.rootLogger.error("Failed to close scope for id: $id", it)
            }
        }
        super.close()
        runCatching { stagedJarPath?.let(Files::deleteIfExists) }
            .onFailure { Microkernel.instance.rootLogger.error("Failed to delete staged jar for id: $id", it) }
    }

    internal fun dependsOn(classLoader: ClassLoader): Boolean = classLoader in dependencyClassLoaders

    override fun loadClass(
        name: String,
        resolve: Boolean,
    ): Class<*> {
        synchronized(getClassLoadingLock(name)) {
            val alreadyLoaded = findLoadedClass(name)
            if (alreadyLoaded != null) {
                if (resolve) {
                    resolveClass(alreadyLoaded)
                }
                return alreadyLoaded
            }

            val fromPublicParent =
                try {
                    parent.loadClass(name)
                } catch (_: ClassNotFoundException) {
                    null
                }
            if (fromPublicParent != null) {
                if (resolve) {
                    resolveClass(fromPublicParent)
                }
                return fromPublicParent
            }

            val fromDependency = loadFromDependencies(name, visited = null)
            if (fromDependency != null) {
                if (resolve) {
                    resolveClass(fromDependency)
                }
                return fromDependency
            }

            val fromPlugin = findClass(name)
            if (resolve) {
                resolveClass(fromPlugin)
            }
            return fromPlugin
        }
    }

    private fun loadFromSelfOrDependencies(
        name: String,
        visited: MutableSet<ClassLoader>,
    ): Class<*>? {
        synchronized(getClassLoadingLock(name)) {
            findLoadedClass(name)?.let { return it }

            loadFromDependencies(name, visited)?.let { return it }

            return try {
                findClass(name)
            } catch (_: ClassNotFoundException) {
                null
            }
        }
    }

    private fun loadFromDependencies(
        name: String,
        visited: MutableSet<ClassLoader>?,
    ): Class<*>? {
        if (dependencyClassLoaders.isEmpty()) {
            return null
        }

        var seen = visited
        for (dependencyClassLoader in dependencyClassLoaders) {
            val fromDependency =
                if (dependencyClassLoader is PluginClassLoader) {
                    val currentVisited = seen ?: HashSet<ClassLoader>(4).also {
                        it.add(this)
                        seen = it
                    }
                    if (currentVisited.add(dependencyClassLoader)) {
                        dependencyClassLoader.loadFromSelfOrDependencies(name, currentVisited)
                    } else {
                        null
                    }
                } else {
                    try {
                        dependencyClassLoader.loadClass(name)
                    } catch (_: ClassNotFoundException) {
                        null
                    }
                }

            if (fromDependency != null) {
                return fromDependency
            }
        }

        return null
    }

    override fun toString(): String = "ID: " + id + " - " + super.toString()
}
