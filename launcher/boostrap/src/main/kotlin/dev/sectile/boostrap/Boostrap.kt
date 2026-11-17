package dev.sectile.boostrap

import dev.sectile.boostrap.logger.LoggersLoader
import dev.sectile.boostrap.network.loadNetwork
import dev.sectile.boostrap.plugin.loadPluginConfig
import dev.sectile.kernel.config.ConfigServiceRegistry
import dev.sectile.microkernel.Microkernel
import dev.sectile.microkernel.plugin.scope.KernelPluginScopeFactory
import dev.sectile.sdk.bundled.config.json.JsonConfigService
import dev.sectile.sdk.bundled.config.yaml.YamlConfigService
import dev.sectile.sdk.bundled.scanner.ScannerProvider

fun startServer(publicClassLoader: ClassLoader, startTime: Long) {
    val configService = YamlConfigService
    ConfigServiceRegistry.register(YamlConfigService)
    ConfigServiceRegistry.register(JsonConfigService)

    val loggersLoader = LoggersLoader()
    val logger = loggersLoader.load(configService)
    startSDKFeatures()

    Microkernel.init(logger, loadPluginConfig(configService), publicClassLoader)

    loadNetwork(configService, logger)

    logger.info("Server started in ${System.currentTimeMillis() - startTime}ms")

    loggersLoader.console?.run()
}

private fun startSDKFeatures() {
    KernelPluginScopeFactory.registerDefaultProvider(ScannerProvider())
}
