package dev.sectile.boostrap.logger

import dev.sectile.kernel.config.ConfigSection
import kotlin.time.Duration.Companion.seconds

class LoggerConfig(section: ConfigSection) {
    val enable = section.getBoolean("enable", true)

    val prefix = PrefixConfig(section.getSection("prefix"))
    val console = ConsoleConfig(section.getSection("console"))
    val logs = FileLogsConfig(section.getSection("logs"))
}

class PrefixConfig(section: ConfigSection) {
    val debug = section.getString("debug", "[DEBUG %time%] ")
    val log = section.getString("log", "[LOG %time%] ")
    val info = section.getString("info", "[INFO %time%] ")
    val warn = section.getString("warn", "[WARN %time%] ")
    val error = section.getString("error", "[ERROR %time%] ")
}

class ConsoleConfig(section: ConfigSection) {
    val enable = section.getBoolean("enable", true)
    val useJLine = section.getBoolean("useJLine", false)
    val levels = LoggerLevels(section.getSection("levels"))
    val styles = StylesConfig(section.getSection("styles"))
}

class StylesConfig(section: ConfigSection) {
    val debug = ConsoleStyle(section.getSection("debug"))
    val log = ConsoleStyle(section.getSection("log"))
    val info = ConsoleStyle(section.getSection("info"))
    val warn = ConsoleStyle(section.getSection("warn"))
    val error = ConsoleStyle(section.getSection("error"))
}

class ConsoleStyle(section: ConfigSection) {
    val prefix = section.getString("prefix", "")
    val text = section.getString("text", "")
    val afterText = section.getString("afterText", "")
}

class FileLogsConfig(section: ConfigSection) {
    val enable = section.getBoolean("enable", true)
    val logsFolder = section.getString("logsFolder", "logs")

    val bufferSize = section.getInt("bufferSize", 8192)
    val maxFileSize = section.getLong("maxFileSize", 5_000_000L)

    val compression = section.getBoolean("compression", true)

    val levels = LoggerLevels(section.getSection("levels"))
    val autoFlush = AutoFlushConfig(section.getSection("auto-flush"))
}

class AutoFlushConfig(section: ConfigSection) {
    val interval = section.getDuration("interval", 10.seconds)
}

class LoggerLevels(section: ConfigSection) {
    val debug = section.getBoolean("debug", false)
    val log = section.getBoolean("log", true)
    val info = section.getBoolean("info", true)
    val warn = section.getBoolean("warn", true)
    val error = section.getBoolean("error", true)
}
