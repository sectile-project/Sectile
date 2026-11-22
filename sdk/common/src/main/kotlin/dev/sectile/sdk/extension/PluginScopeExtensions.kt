package dev.sectile.sdk.extension

import dev.sectile.engine.api.command.CommandRegistry
import dev.sectile.engine.api.world.WorldRegistry
import dev.sectile.kernel.Kernel
import dev.sectile.kernel.event.EventBus
import dev.sectile.kernel.logger.LoggerSpy
import dev.sectile.kernel.plugin.scope.PluginScope
import dev.sectile.kernel.plugin.spi.lifecycle.PluginLifecycle
import dev.sectile.kernel.scheduler.Scheduler
import dev.sectile.network.api.packet.handler.PacketHandlerRegistry
import dev.sectile.network.api.player.stage.StageHookRegistry
import dev.sectile.sdk.scanner.PackageScanner
import dev.sectile.sdk.scanner.ScanResult
import java.util.concurrent.ExecutorService

fun PluginScope.eventbus(): EventBus = this[EventBus::class.java] ?: throw IllegalStateException("EventBus not found in the current PluginScope")

fun PluginScope.commands(): CommandRegistry = this[CommandRegistry::class.java] ?: throw IllegalStateException("CommandRegistry not found in the current PluginScope")

fun PluginScope.createScanner(): PackageScanner = this[PackageScanner::class.java] ?: throw IllegalStateException("PackageScanner not found in the current PluginScope")

fun PluginScope.scan(
    block: PackageScanner.() -> Unit,
): ScanResult {
    val scanner = createScanner()
    scanner.block()
    return scanner.scan()
}

val PluginLifecycle.scope: PluginScope
    get() = Kernel.pluginScopeFactory.acquire(this)

fun PluginScope.worlds(): WorldRegistry = this[WorldRegistry::class.java] ?: throw IllegalStateException("WorldRegistry not found in the current PluginScope")

fun PluginScope.packetHandlers(): PacketHandlerRegistry = this[PacketHandlerRegistry::class.java] ?: throw IllegalStateException("PacketHandlerRegistry not found in the current PluginScope")

fun PluginScope.stageHooks(): StageHookRegistry = this[StageHookRegistry::class.java] ?: throw IllegalStateException("StageHookRegistry not found in the current PluginScope")

fun PluginScope.scheduler(): Scheduler = this[Scheduler::class.java] ?: throw IllegalStateException("Scheduler not found in the current PluginScope")

fun PluginScope.virtualThreads(): ExecutorService = this[ExecutorService::class.java] ?: throw IllegalStateException("ExecutorService not found in the current PluginScope")

fun PluginScope.threadPool(
    threads: Int,
    name: String = "worker",
): ExecutorService = Kernel.schedulerProvider.createThreadPool(threads, name)

fun PluginScope.loggerSpy(): LoggerSpy = Kernel.loggerSpy
