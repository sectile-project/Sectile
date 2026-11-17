package dev.sectile.boostrap.logger.type

import dev.sectile.boostrap.logger.LogRingBuffer
import dev.sectile.kernel.Kernel
import dev.sectile.kernel.logger.LogLevel
import dev.sectile.kernel.logger.Logger
import dev.sectile.kernel.logger.LoggerFormatter
import dev.sectile.microkernel.plugin.scope.provider.ScopedLoggerSpy

class BoostrapLogger(
    override val loggerFormatter: LoggerFormatter,
    private val ringBuffer: LogRingBuffer,
    private val debugPrefix: String,
    private val logPrefix: String,
    private val infoPrefix: String,
    private val warnPrefix: String,
    private val errorPrefix: String,
) : Logger {

    private fun getSpyLoggers(): Set<Logger> {
        val spy = Kernel.loggerSpy as ScopedLoggerSpy
        return spy.getLoggers()
    }

    override fun debug(message: String) {
        ringBuffer.push(LogLevel.DEBUG, debugPrefix, message, null)

        getSpyLoggers().forEach { it.debug(message) }
    }

    override fun log(message: String) {
        ringBuffer.push(LogLevel.LOG, logPrefix, message, null)

        getSpyLoggers().forEach { it.log(message) }
    }

    override fun info(message: String) {
        ringBuffer.push(LogLevel.INFO, infoPrefix, message, null)

        getSpyLoggers().forEach { it.info(message) }
    }

    override fun warn(message: String) {
        ringBuffer.push(LogLevel.WARN, warnPrefix, message, null)

        getSpyLoggers().forEach { it.warn(message) }
    }

    override fun error(message: String) {
        ringBuffer.push(LogLevel.ERROR, errorPrefix, message, null)

        getSpyLoggers().forEach { it.error(message) }
    }

    override fun error(message: String, throwable: Throwable) {
        ringBuffer.push(LogLevel.ERROR, errorPrefix, message, throwable)

        getSpyLoggers().forEach { it.error(message, throwable) }
    }
}
