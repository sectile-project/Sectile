package dev.sectile.plugin.discordhook

import dev.sectile.kernel.Kernel
import dev.sectile.kernel.plugin.spi.lifecycle.BasePluginLifecycle
import dev.sectile.plugin.discordhook.config.loadDiscordConfig
import dev.sectile.plugin.discordhook.webhook.DiscordWebHook
import dev.sectile.sdk.extension.scope
import dev.sectile.sdk.plugin.annotation.Plugin

@Plugin(id = "discordhook", version = "1.0.0")
class DiscordHookPlugin : BasePluginLifecycle() {

    override fun enable() {
        DiscordWebHook.init()
        scope.onClose { DiscordWebHook.shutdown() }
        val config = loadDiscordConfig()
        Kernel.loggerSpy.register(DiscordLogger(config))
    }
}
