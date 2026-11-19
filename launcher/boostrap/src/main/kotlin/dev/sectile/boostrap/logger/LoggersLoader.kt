package dev.sectile.boostrap.logger

import dev.sectile.boostrap.console.Console
import dev.sectile.boostrap.console.ConsoleSender
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
import java.time.format.DateTimeFormatter

class LoggersLoader {

    var console: Console? = null
    val consoleSender: ConsoleSender = ConsoleSender()
    private var printWriter: PrintWriter? = null
    private var lineReader: LineReader? = null

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
        val loggerFormatter = LogFormatter(DateTimeFormatter.ofPattern(prefix.dateFormatter))

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

    private fun createConsole(config: LoggerConfig) {
        if (config.console.useJLine) {
            val jLineConsole = createJLineConsole(consoleSender)
            if (jLineConsole != null) {
                console = jLineConsole
                printWriter = jLineConsole.terminal.writer()
                lineReader = jLineConsole.reader
                return
            }
        }
        console = MinimalConsole(consoleSender)
        printWriter = PrintWriter(System.out)
        lineReader = null
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
        )
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
