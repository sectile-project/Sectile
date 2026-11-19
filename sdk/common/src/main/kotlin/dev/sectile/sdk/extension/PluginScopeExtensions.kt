package dev.sectile.sdk.extension

import dev.sectile.engine.api.command.CommandRegistry
import dev.sectile.kernel.Kernel
import dev.sectile.kernel.event.EventBus
import dev.sectile.kernel.plugin.scope.PluginScope
import dev.sectile.kernel.plugin.spi.lifecycle.PluginLifecycle
import dev.sectile.sdk.scanner.PackageScanner
import dev.sectile.sdk.scanner.ScanResult

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
    get() = Kernel.pluginScopeFactory.acquire()
