package dev.sectile.boostrap

import dev.sectile.boostrap.command.HelpCommand
import dev.sectile.boostrap.command.PluginsCommand
import dev.sectile.boostrap.command.StopCommand
import dev.sectile.boostrap.command.WhereAmICommand
import dev.sectile.boostrap.command.WhoCommand
import dev.sectile.boostrap.command.authCommand
import dev.sectile.boostrap.console.Console
import dev.sectile.boostrap.logger.LoggersLoader
import dev.sectile.boostrap.network.loadNetwork
import dev.sectile.boostrap.plugin.loadPluginConfig
import dev.sectile.engine.api.Engine
import dev.sectile.engine.command.CommandBootstrap
import dev.sectile.engine.command.DefaultCommandRegistry
import dev.sectile.engine.command.scope.ScopedCommandRegistryProvider
import dev.sectile.engine.internal.registry.InternalPlayerRegistry
import dev.sectile.engine.internal.registry.InternalWorldRegistry
import dev.sectile.engine.world.WorldBootstrap
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

    val pluginStarter = Microkernel.init(logger, loadPluginConfig(configService), publicClassLoader)

    startEngine()
    startSDK()

    pluginStarter.run()

    val console = loggersLoader.resolveConsole()
    registerBuiltinCommands(console)

    loadNetwork(configService, logger)
    WorldBootstrap.init(logger)
    CommandBootstrap.initNetwork(logger)

    logger.info("Server started in ${System.currentTimeMillis() - startTime}ms")

    console?.run()
}

private fun startSDK() {
    KernelPluginScopeFactory.registerDefaultProvider(ScannerProvider())
}

private fun startEngine() {
    Engine.init(InternalPlayerRegistry, DefaultCommandRegistry(), InternalWorldRegistry)
    KernelPluginScopeFactory.registerDefaultProvider(ScopedCommandRegistryProvider(Engine.commands))
}

private fun registerBuiltinCommands(console: Console?) {
    val registry = Engine.commands
    registry.register(
        HelpCommand(registry),
        WhereAmICommand(),
        WhoCommand(),
        PluginsCommand(),
        authCommand(),
        StopCommand(console),
    )
}
