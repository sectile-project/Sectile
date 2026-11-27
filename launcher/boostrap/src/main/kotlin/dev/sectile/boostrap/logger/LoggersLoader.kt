package dev.sectile.boostrap.logger

import dev.sectile.boostrap.console.Console
import dev.sectile.boostrap.console.ConsoleSender
import dev.sectile.boostrap.console.InteractiveConsoleConfig
import dev.sectile.boostrap.console.loadConsoleConfig
import dev.sectile.boostrap.console.minimal.MinimalConsole
import dev.sectile.boostrap.console.terminal.TerminalConsole
import dev.sectile.boostrap.logger.console.ConsoleAppender
import dev.sectile.boostrap.logger.file.FileAppender
import dev.sectile.boostrap.logger.file.LogCompressor
import dev.sectile.boostrap.logger.type.BoostrapLogger
import dev.sectile.boostrap.logger.type.NoOpLogger
import dev.sectile.kernel.Kernel
import dev.sectile.kernel.config.ConfigService
import dev.sectile.kernel.logger.JavaLoggerFormatter
import dev.sectile.kernel.logger.LogLevel
import dev.sectile.kernel.logger.Logger
import java.io.File
import java.io.IOException
import java.io.PrintWriter
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

class LoggersLoader {

    var console: Console? = null
    val consoleSender: ConsoleSender = ConsoleSender()
    private var printWriter: PrintWriter? = null
    private var consoleAppender: ConsoleAppender? = null

    fun load(configService: ConfigService): Logger {
        val consoleConfig = loadConsoleConfig(configService)
        val section = configService.createIfAbsentAndLoad("logger")
        val config = LoggerConfig(section)
        if (!config.enable) {
            createConsole(consoleConfig)
            return NoOpLogger()
        }

        createConsole(consoleConfig)

        val ringBuffer = LogRingBuffer()
        val consoleAppender = loadConsoleLogger(config.console)
        (console as? TerminalConsole)?.let { terminalConsole ->
            consoleAppender?.attachTerminal(terminalConsole.lineReader::printAbove)
        }
        val fileAppender = loadFileLogger(config.logs)
        val autoFlush = config.logs.autoFlush

        val prefix = config.prefix
        val loggerFormatter = LogFormatter()

        val processor = LogProcessorThread(
            ringBuffer,
            autoFlush.interval.inWholeSeconds,
            consoleAppender,
            fileAppender,
            loggerFormatter,
        )

        processor.start()
        Kernel.addStopTask {
            processor.shutdown()
            try {
                processor.join(5000)
            } catch (_: InterruptedException) {}
        }
        val enabledLevels = computeEnabledLevels(
            if (consoleAppender != null) config.console.levels else null,
            if (fileAppender != null) config.logs.levels else null,
        )
        val logger = BoostrapLogger(
            JavaLoggerFormatter,
            ringBuffer,
            prefix.debug,
            prefix.log,
            prefix.info,
            prefix.warn,
            prefix.error,
            enabledLevels,
        )
        consoleSender.attachLogger(logger)

        return logger
    }

    fun resolveConsole(): Console? = console

    private fun createConsole(config: InteractiveConsoleConfig) {
        printWriter = PrintWriter(System.out)
        console = if (config.interactive) {
            runCatching { TerminalConsole(consoleSender, config.terminal) }
                .getOrElse { MinimalConsole(consoleSender) }
        } else {
            MinimalConsole(consoleSender)
        }
    }

    private fun loadConsoleLogger(console: ConsoleConfig): ConsoleAppender? {
        if (!console.enable) {
            return null
        }
        val styles = console.styles
        return ConsoleAppender(
            printWriter,
            console.levels,
            styles.debug,
            styles.log,
            styles.info,
            styles.warn,
            styles.error,
        ).also { consoleAppender = it }
    }

    private fun loadFileLogger(logs: FileLogsConfig): FileAppender? {
        if (!logs.enable) {
            return null
        }

        val file = File("${logs.logsFolder}/latest.log")

        compressOldLog(file, logs.compression)
        val channel = createLogChannel(file.toPath()) ?: return null

        return FileAppender(logs.maxFileSize, logs.bufferSize, logs.levels, channel)
    }

    private fun compressOldLog(path: File, compression: Boolean) {
        try {
            if (path.exists() && path.length() > 0) {
                LogCompressor.compress(path, compression)
            }
        } catch (e: IOException) {
            System.err.println("Can't compress old log file")
            e.printStackTrace(System.err)
        }
    }

    private fun computeEnabledLevels(vararg levelsList: LoggerLevels?): Int {
        var mask = 0
        for (levels in levelsList) {
            if (levels == null) continue
            if (levels.debug) mask = mask or (1 shl LogLevel.DEBUG.ordinal)
            if (levels.log) mask = mask or (1 shl LogLevel.LOG.ordinal)
            if (levels.info) mask = mask or (1 shl LogLevel.INFO.ordinal)
            if (levels.warn) mask = mask or (1 shl LogLevel.WARN.ordinal)
            if (levels.error) mask = mask or (1 shl LogLevel.ERROR.ordinal)
        }
        return mask
    }

    private fun createLogChannel(path: Path): FileChannel? = try {
        path.parent?.let { Files.createDirectories(it) }
        FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.WRITE)
    } catch (e: IOException) {
        System.err.println("Can't start file logger")
        e.printStackTrace(System.err)
        null
    }
}
