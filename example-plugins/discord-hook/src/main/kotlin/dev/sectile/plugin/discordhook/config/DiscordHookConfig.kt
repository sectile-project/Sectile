package dev.sectile.plugin.discordhook.config

import dev.sectile.kernel.config.ConfigSection
import dev.sectile.kernel.config.ConfigServiceRegistry
import java.time.format.DateTimeFormatter
import java.util.Objects

fun loadDiscordConfig(): DiscordHookConfig = DiscordHookConfig(ConfigServiceRegistry["yaml"].createIfAbsentAndLoad("config"))

class DiscordHookConfig(
    section: ConfigSection,
) {
    val debug = LevelSection(section.getSection("debug"))
    val log = LevelSection(section.getSection("log"))
    val info = LevelSection(section.getSection("info"))
    val warn = LevelSection(section.getSection("warn"))
    val error = LevelSection(section.getSection("error"))

    val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern(
        section.getString("date-format", "HH:mm:ss"),
    )
}

class LevelSection(
    section: ConfigSection,
) {
    val enable: Boolean = section.getBoolean("enable", false)
    val webhookUrl: String = section.getString("webhookUrl", "")
    val title: String = section.getString("title", section.name)
    val description: String = objectToString(section["description"])
}

private fun objectToString(any: Any?): String {
    if (any is String) return any
    if (any is List<*>) {
        return any.joinToString("\n") { objectToString(it) }
    }
    return Objects.toString(any)
}
