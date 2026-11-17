package dev.sectile.plugin.debug

import dev.sectile.kernel.config.ConfigSection
import dev.sectile.kernel.config.ConfigServiceRegistry
import kotlin.time.Duration.Companion.seconds

fun loadConfig(): DebugConfig = DebugConfig(ConfigServiceRegistry["yaml"].createIfAbsentAndLoad("config"))

class DebugConfig(
    section: ConfigSection,
) {
    val testString: String = section.getString("testString", "default-String")
    val exampleDuration = section.getDuration("exampleDuration", 20.seconds)
}
