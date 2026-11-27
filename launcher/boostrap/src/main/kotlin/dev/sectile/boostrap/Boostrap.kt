package dev.sectile.boostrap

import dev.sectile.boostrap.command.authCommand
import dev.sectile.boostrap.command.helpCommand
import dev.sectile.boostrap.command.pluginCommand
import dev.sectile.boostrap.command.stopCommand
import dev.sectile.boostrap.command.whereAmICommand
import dev.sectile.boostrap.command.whoCommand
import dev.sectile.boostrap.console.Console
import dev.sectile.boostrap.logger.LoggersLoader
import dev.sectile.boostrap.network.loadNetwork
import dev.sectile.boostrap.plugin.loadPluginConfig
import dev.sectile.engine.api.Engine
import dev.sectile.engine.api.command.CommandRegistry
import dev.sectile.engine.api.world.WorldRegistry
import dev.sectile.engine.command.CommandBootstrap
import dev.sectile.engine.command.DefaultCommandRegistry
import dev.sectile.engine.command.scope.ScopedCommandRegistryProvider
import dev.sectile.engine.internal.registry.InternalPlayerRegistry
import dev.sectile.engine.internal.registry.InternalWorldRegistry
import dev.sectile.engine.internal.scope.ScopedWorldRegistryProvider
import dev.sectile.engine.world.WorldBootstrap
import dev.sectile.kernel.config.ConfigServiceRegistry
import dev.sectile.kernel.plugin.scope.provider.ProtectedLazyProvider
import dev.sectile.microkernel.Microkernel
import dev.sectile.microkernel.plugin.scope.KernelPluginScopeFactory
import dev.sectile.network.api.packet.handler.PacketHandlerRegistry
import dev.sectile.network.api.player.stage.StageHookRegistry
import dev.sectile.network.internal.NetworkInternal
import dev.sectile.network.internal.packet.scope.ScopedPacketHandlerProvider
import dev.sectile.network.internal.stages.ScopedStageHookProvider
import dev.sectile.network.internal.stages.StageHooks
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

    val commandRegistry = startEngine()
    startSDK()

    pluginStarter.run()

    val console = loggersLoader.resolveConsole()
    registerBuiltinCommands(console, commandRegistry)
    loadNetwork(configService, logger)
    WorldBootstrap.init(logger, NetworkInternal.packetHandlers, StageHooks)
    CommandBootstrap.initNetwork(logger, NetworkInternal.packetHandlers, StageHooks)

    logger.info("Server started in ${System.currentTimeMillis() - startTime}ms")

    console?.run()
}

private fun startSDK() {
    KernelPluginScopeFactory.registerDefaultProvider { ScannerProvider() }
    KernelPluginScopeFactory.registerDefaultProvider {
        ProtectedLazyProvider(PacketHandlerRegistry::class.java) {
            ScopedPacketHandlerProvider(NetworkInternal.packetHandlers)
        }
    }
    KernelPluginScopeFactory.registerDefaultProvider {
        ProtectedLazyProvider(StageHookRegistry::class.java) {
            ScopedStageHookProvider(StageHooks)
        }
    }
}

private fun startEngine(): CommandRegistry {
    val commandRegistry = DefaultCommandRegistry()
    Engine.init(InternalPlayerRegistry, commandRegistry, InternalWorldRegistry)
    KernelPluginScopeFactory.registerDefaultProvider {
        ProtectedLazyProvider(CommandRegistry::class.java) { ScopedCommandRegistryProvider(commandRegistry) }
    }
    KernelPluginScopeFactory.registerDefaultProvider {
        ProtectedLazyProvider(WorldRegistry::class.java) { ScopedWorldRegistryProvider(InternalWorldRegistry) }
    }
    return commandRegistry
}

private fun registerBuiltinCommands(console: Console?, registry: CommandRegistry) {
    registry.register(
        helpCommand(),
        whereAmICommand(),
        whoCommand(),
        pluginCommand(),
        authCommand(),
        stopCommand(console),
    )
}
