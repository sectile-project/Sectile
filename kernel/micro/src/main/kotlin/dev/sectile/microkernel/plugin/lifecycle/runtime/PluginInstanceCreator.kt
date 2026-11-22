package dev.sectile.microkernel.plugin.lifecycle.runtime

import dev.sectile.kernel.logger.Logger
import dev.sectile.kernel.plugin.exception.PluginLoadException
import dev.sectile.kernel.plugin.library.LibrariesRequest
import dev.sectile.kernel.plugin.model.PluginId
import dev.sectile.kernel.plugin.spi.lifecycle.PluginBoostrap
import dev.sectile.kernel.plugin.spi.lifecycle.PluginLifecycle
import dev.sectile.microkernel.plugin.catalog.DefaultPluginCatalog
import dev.sectile.microkernel.plugin.catalog.PluginEntry
import dev.sectile.microkernel.plugin.classloading.PluginClassLoader
import dev.sectile.microkernel.plugin.library.PluginLibraryResolver
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

internal class PluginInstanceCreator(
    private val catalog: DefaultPluginCatalog,
    private val libraryResolver: PluginLibraryResolver,
    private val logger: Logger,
    private val parentClassLoader: ClassLoader,
) {
    fun create(entry: PluginEntry): LoadedPlugin {
        val manifest = entry.candidate.manifest
        val dependencyClassLoaders = directClassLoaderParentsOf(entry)
        val libraryUrls: List<URL> =
            if (manifest.boostrapClass == null) {
                emptyList()
            } else {
                val librariesRequest = LibrariesRequest()
                runBootstrap(entry, manifest.boostrapClass, dependencyClassLoaders, librariesRequest)

                try {
                    libraryResolver.resolve(librariesRequest)
                } catch (error: Throwable) {
                    throw PluginLoadException("Could not resolve libraries for plugin ${entry.id}.", error)
                }
            }

        val stagedJar = stageJar(entry)
        val runtimeClassLoader =
            try {
                val pluginUrl = stagedJar.toUri().toURL()
                val classLoaderUrls =
                    if (libraryUrls.isEmpty()) {
                        arrayOf(pluginUrl)
                    } else {
                        Array(libraryUrls.size + 1) { index ->
                            if (index == 0) pluginUrl else libraryUrls[index - 1]
                        }
                    }

                PluginClassLoader(
                    entry.id,
                    classLoaderUrls,
                    dependencyClassLoaders,
                    parentClassLoader,
                    stagedJar,
                )
            } catch (error: Throwable) {
                runCatching { Files.deleteIfExists(stagedJar) }
                throw PluginLoadException("Could not prepare classloader for plugin ${entry.id}.", error)
            }

        return try {
            val lifecycle =
                withPluginClassLoader(runtimeClassLoader) {
                    instantiate(runtimeClassLoader, manifest.mainClass, PluginLifecycle::class.java)
                }

            LoadedPlugin(runtimeClassLoader, lifecycle)
        } catch (error: Throwable) {
            runCatching { runtimeClassLoader.close() }
                .onFailure { closeError -> logger.error("Could not close runtime classloader for plugin ${entry.id}.", closeError) }
            throw PluginLoadException("Could not create lifecycle for plugin ${entry.id}.", error)
        }
    }

    private fun runBootstrap(
        entry: PluginEntry,
        bootstrapClassName: String,
        dependencyClassLoaders: List<ClassLoader>,
        librariesRequest: LibrariesRequest,
    ) {
        val stagedJar = stageJar(entry)
        val bootstrapClassLoader =
            try {
                PluginClassLoader(
                    entry.id,
                    arrayOf(stagedJar.toUri().toURL()),
                    dependencyClassLoaders,
                    parentClassLoader,
                    stagedJar,
                )
            } catch (error: Throwable) {
                runCatching { Files.deleteIfExists(stagedJar) }
                throw PluginLoadException("Could not prepare bootstrap classloader for plugin ${entry.id}.", error)
            }

        try {
            withPluginClassLoader(bootstrapClassLoader) {
                val bootstrap = instantiate(bootstrapClassLoader, bootstrapClassName, PluginBoostrap::class.java)
                bootstrap.boot(librariesRequest)
            }
        } catch (error: Throwable) {
            throw PluginLoadException("Could not run bootstrap for plugin ${entry.id}.", error)
        } finally {
            runCatching { bootstrapClassLoader.close() }
                .onFailure { error -> logger.error("Could not close bootstrap classloader for plugin ${entry.id}.", error) }
        }
    }

    private fun stageJar(entry: PluginEntry): Path {
        val source = entry.candidate.jarFile.toPath()
        val staged = Files.createTempFile("sectile-plugin-${entry.id.value}-", ".jar")
        Files.copy(source, staged, StandardCopyOption.REPLACE_EXISTING)
        return staged
    }

    private fun directClassLoaderParentsOf(entry: PluginEntry): List<ClassLoader> {
        val dependencies = entry.metadata.dependencies
        if (dependencies.isEmpty()) {
            return emptyList()
        }

        val result = ArrayList<ClassLoader>(dependencies.size)
        var seenIds: HashSet<PluginId>? = null

        for (dependency in dependencies) {
            val classLoader = catalog.entry(dependency.id)?.classLoader ?: continue
            val seen = seenIds ?: HashSet<PluginId>(dependencies.size).also { seenIds = it }
            if (seen.add(dependency.id)) {
                result.add(classLoader)
            }
        }

        return result
    }

    private fun <T : Any> instantiate(
        classLoader: ClassLoader,
        className: String,
        expectedType: Class<T>,
    ): T {
        val clazz = Class.forName(className, true, classLoader).asSubclass(expectedType)
        val constructor = clazz.getDeclaredConstructor()
        constructor.isAccessible = true
        return constructor.newInstance()
    }
}

internal data class LoadedPlugin(
    val classLoader: PluginClassLoader,
    val lifecycle: PluginLifecycle,
)
