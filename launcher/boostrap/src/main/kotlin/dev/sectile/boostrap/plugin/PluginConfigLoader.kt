package dev.sectile.boostrap.plugin

import dev.sectile.kernel.config.ConfigSection
import dev.sectile.kernel.config.ConfigService
import dev.sectile.kernel.plugin.model.PluginId
import dev.sectile.kernel.plugin.model.version.Version
import dev.sectile.microkernel.config.KernelPluginConfig
import java.nio.file.Path
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

fun loadPluginConfig(configService: ConfigService): KernelPluginConfig {
    val config = configService.createIfAbsentAndLoad("plugins")

    val pathsSection = config.getSection("paths")
    val selectionSection = config.getSection("selection")
    val loadingSection = config.getSection("loading")
    val loggingSection = config.getSection("logging")
    val startupBufferSection = loggingSection.getSection("startup-buffer")

    return KernelPluginConfig(
        loadPaths(pathsSection),
        loadSelection(selectionSection),
        loadLoadingSection(loadingSection),
        loadLogging(loggingSection, startupBufferSection),
    )
}

private fun loadPaths(section: ConfigSection): KernelPluginConfig.Paths = KernelPluginConfig.Paths(
    Path.of(section.getString("plugins", "/plugins")),
    Path.of(section.getString("libraries", "/cache/libs")),
)

private fun loadSelection(section: ConfigSection): KernelPluginConfig.Selection {
    val plugins = section.getStringList("plugins")
        .asSequence()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .map(::parsePluginFormat)
        .toList()

    val duplicates = plugins.groupBy { it.id }
        .filterValues { it.size > 1 }

    require(duplicates.isEmpty()) {
        "Duplicate plugin entries: ${duplicates.keys.joinToString()}"
    }

    return KernelPluginConfig.Selection(
        section.getEnum("mode", KernelPluginConfig.Selection.Mode.ALL),
        plugins,
    )
}

private fun loadLoadingSection(section: ConfigSection): KernelPluginConfig.Loading {
    val timeOut = Duration.parseOrNull(section.getString("timeout", "5s")) ?: 5.seconds
    return KernelPluginConfig.Loading(
        section.getBoolean("parallel", true),
        timeOut,
    )
}

private fun loadLogging(
    loggingSection: ConfigSection,
    startupBufferSection: ConfigSection,
): KernelPluginConfig.Logging = KernelPluginConfig.Logging(
    loggingSection.getBoolean("lifecycle-events", true),
    startupBufferSection.getBoolean("enabled", true),
    startupBufferSection
        .getInt("initial-capacity", 64)
        .coerceAtLeast(1),
    startupBufferSection
        .getInt("max-lines", 1000)
        .coerceAtLeast(1),
    startupBufferSection.getEnum(
        "overflow-strategy",
        KernelPluginConfig.Logging.Strategy.DROP_NEWEST,
    ),
)

private fun parsePluginFormat(plugin: String): KernelPluginConfig.Selection.PluginFormat {
    val separatorIndex = plugin.indexOf(':')

    if (separatorIndex < 0) {
        return KernelPluginConfig.Selection.PluginFormat(
            id = PluginId(plugin),
            version = null,
        )
    }

    val id = PluginId(plugin.substring(0, separatorIndex).trim())
    val versionRaw = plugin.substring(separatorIndex + 1).trim()

    require(versionRaw.isNotEmpty()) {
        "Invalid plugin selection entry '$plugin': version cannot be blank after ':'."
    }

    return KernelPluginConfig.Selection.PluginFormat(id, Version.parse(versionRaw))
}
