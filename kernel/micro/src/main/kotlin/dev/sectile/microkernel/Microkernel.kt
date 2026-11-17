package dev.sectile.microkernel

import dev.sectile.kernel.Kernel
import dev.sectile.kernel.event.EventBus
import dev.sectile.kernel.logger.Logger
import dev.sectile.kernel.scheduler.SchedulerProvider
import dev.sectile.microkernel.config.KernelPluginConfig
import dev.sectile.microkernel.event.bus.DefaultEventBus
import dev.sectile.microkernel.logger.MicrokernelLoggerFactory
import dev.sectile.microkernel.plugin.PluginInstaller
import dev.sectile.microkernel.plugin.PluginStarter
import dev.sectile.microkernel.plugin.catalog.DefaultPluginCatalog
import dev.sectile.microkernel.plugin.library.LibraryPathResolver
import dev.sectile.microkernel.plugin.library.PluginLibraryResolver
import dev.sectile.microkernel.plugin.lifecycle.DefaultPluginLifecycleControl
import dev.sectile.microkernel.plugin.manifest.PluginPropertiesReader
import dev.sectile.microkernel.plugin.scanner.PluginScanner
import dev.sectile.microkernel.plugin.scope.KernelPluginScopeFactory
import dev.sectile.microkernel.plugin.scope.provider.ScopedLoggerSpy
import dev.sectile.microkernel.plugin.validation.PluginDependencyValidator
import dev.sectile.microkernel.scheduler.KernelSchedulerProvider

class Microkernel private constructor(
    val pluginConfig: KernelPluginConfig,
    val rootLogger: Logger,
) {
    companion object {
        @Volatile
        private var ref: Microkernel? = null

        val instance: Microkernel
            get() = ref ?: error("Microkernel not initialized.")

        fun init(
            logger: Logger,
            kernelPluginConfig: KernelPluginConfig,
            parentClassLoader: ClassLoader = Kernel::class.java.classLoader,
            eventBus: EventBus = DefaultEventBus(logger),
            schedulerProvider: SchedulerProvider = KernelSchedulerProvider(),
        ) {
            check(ref == null) { "Microkernel already initialized." }

            val pluginCatalog = DefaultPluginCatalog()
            val libraryResolver = PluginLibraryResolver(LibraryPathResolver(kernelPluginConfig.paths.libraries))

            val pluginLifecycleControl =
                DefaultPluginLifecycleControl(
                    pluginCatalog,
                    libraryResolver,
                    kernelPluginConfig,
                    logger,
                    parentClassLoader,
                )

            Kernel.init(
                eventBus,
                MicrokernelLoggerFactory(logger),
                ScopedLoggerSpy,
                schedulerProvider,
                pluginCatalog,
                pluginLifecycleControl,
                KernelPluginScopeFactory,
                kernelPluginConfig.paths.plugins,
                kernelPluginConfig.paths.libraries,
            )

            ref = Microkernel(kernelPluginConfig, logger)

            Kernel.addStopTask {
                pluginLifecycleControl.shutdown()
            }

            val scanner = PluginScanner(PluginPropertiesReader(), logger)
            val installer =
                PluginInstaller(
                    kernelPluginConfig,
                    pluginCatalog,
                    scanner,
                    PluginDependencyValidator(),
                    logger,
                )
            val selectedPluginIds =
                installer.install(
                    kernelPluginConfig.paths.plugins.toFile(),
                )

            PluginStarter(
                kernelPluginConfig,
                pluginCatalog,
                pluginLifecycleControl,
                logger,
            ).start(selectedPluginIds)
        }
    }
}
