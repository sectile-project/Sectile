package dev.sectile.boostrap.logger

import dev.sectile.boostrap.console.Console
import dev.sectile.boostrap.console.ConsoleSender
import dev.sectile.boostrap.console.jline.JLineConsole
import dev.sectile.boostrap.console.jline.createJLineConsole
import dev.sectile.boostrap.console.minimal.MinimalConsole
import dev.sectile.boostrap.logger.console.ConsoleAppender
import dev.sectile.boostrap.logger.file.FileAppender
import dev.sectile.boostrap.logger.file.LogCompressor
import dev.sectile.boostrap.logger.type.BoostrapLogger
import dev.sectile.boostrap.logger.type.NoOpLogger
import dev.sectile.kernel.Kernel
import dev.sectile.kernel.config.ConfigService
import dev.sectile.kernel.logger.JavaLoggerFormatter
import dev.sectile.kernel.logger.Logger
import org.jline.reader.LineReader
import java.io.File
import java.io.IOException
import java.io.PrintWriter
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.concurrent.CompletableFuture

class LoggersLoader {

    var console: Console? = null
    val consoleSender: ConsoleSender = ConsoleSender()
    private var printWriter: PrintWriter? = null
    private var lineReader: LineReader? = null
    private var consoleAppender: ConsoleAppender? = null
    private var consoleFuture: CompletableFuture<Console>? = null

    fun load(configService: ConfigService): Logger {
        val section = configService.createIfAbsentAndLoad("logger")
        val config = LoggerConfig(section)
        if (!config.enable) {
            console = MinimalConsole(consoleSender)
            return NoOpLogger()
        }

        createConsole(config)

        val ringBuffer = LogRingBuffer()
        val consoleAppender = loadConsoleLogger(config.console)
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
        val logger = BoostrapLogger(
            JavaLoggerFormatter,
            ringBuffer,
            prefix.debug,
            prefix.log,
            prefix.info,
            prefix.warn,
            prefix.error,
        )
        consoleSender.attachLogger(logger)

        return logger
    }

    fun resolveConsole(): Console? {
        val pending = consoleFuture ?: return console
        val resolved = pending.join()
        consoleFuture = null
        console = resolved
        if (resolved is JLineConsole) {
            consoleAppender?.attachJLine(resolved.terminal.writer(), resolved.reader)
        }
        return resolved
    }

    private fun createConsole(config: LoggerConfig) {
        printWriter = PrintWriter(System.out)
        lineReader = null

        if (config.console.useJLine) {
            // Build JLine terminal and reader off the startup thread; attached before console.run().
            consoleFuture = CompletableFuture.supplyAsync {
                createJLineConsole(consoleSender) ?: MinimalConsole(consoleSender)
            }
            return
        }
        console = MinimalConsole(consoleSender)
    }

    private fun loadConsoleLogger(console: ConsoleConfig): ConsoleAppender? {
        if (!console.enable) {
            return null
        }
        val styles = console.styles
        return ConsoleAppender(
            printWriter,
            lineReader,
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

    private fun createLogChannel(path: Path): FileChannel? = try {
        path.parent?.let { Files.createDirectories(it) }
        FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.WRITE)
    } catch (e: IOException) {
        System.err.println("Can't start file logger")
        e.printStackTrace(System.err)
        null
    }
}
