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
    private val enabledLevels: Int = ALL_LEVELS,
) : Logger {

    private fun getSpyLoggers(): Set<Logger> {
        val spy = Kernel.loggerSpy as ScopedLoggerSpy
        return spy.getLoggers()
    }

    private fun isEnabled(level: LogLevel): Boolean = (enabledLevels and (1 shl level.ordinal)) != 0

    override fun debug(message: String) {
        if (isEnabled(LogLevel.DEBUG)) {
            ringBuffer.push(LogLevel.DEBUG, debugPrefix, message, null)
        }
        val spies = getSpyLoggers()
        if (spies.isNotEmpty()) {
            spies.forEach { it.debug(message) }
        }
    }

    override fun log(message: String) {
        if (isEnabled(LogLevel.LOG)) {
            ringBuffer.push(LogLevel.LOG, logPrefix, message, null)
        }
        val spies = getSpyLoggers()
        if (spies.isNotEmpty()) {
            spies.forEach { it.log(message) }
        }
    }

    override fun info(message: String) {
        if (isEnabled(LogLevel.INFO)) {
            ringBuffer.push(LogLevel.INFO, infoPrefix, message, null)
        }
        val spies = getSpyLoggers()
        if (spies.isNotEmpty()) {
            spies.forEach { it.info(message) }
        }
    }

    override fun warn(message: String) {
        if (isEnabled(LogLevel.WARN)) {
            ringBuffer.push(LogLevel.WARN, warnPrefix, message, null)
        }
        val spies = getSpyLoggers()
        if (spies.isNotEmpty()) {
            spies.forEach { it.warn(message) }
        }
    }

    override fun error(message: String) {
        if (isEnabled(LogLevel.ERROR)) {
            ringBuffer.push(LogLevel.ERROR, errorPrefix, message, null)
        }
        val spies = getSpyLoggers()
        if (spies.isNotEmpty()) {
            spies.forEach { it.error(message) }
        }
    }

    override fun error(message: String, throwable: Throwable) {
        if (isEnabled(LogLevel.ERROR)) {
            ringBuffer.push(LogLevel.ERROR, errorPrefix, message, throwable)
        }
        val spies = getSpyLoggers()
        if (spies.isNotEmpty()) {
            spies.forEach { it.error(message, throwable) }
        }
    }

    companion object {
        const val ALL_LEVELS: Int = -1
    }
}
