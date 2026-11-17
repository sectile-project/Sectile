package dev.sectile.network.internal.auth

import dev.sectile.kernel.logger.LogLevel
import dev.sectile.kernel.logger.Logger
import dev.sectile.kernel.logger.LoggerFormatter

/**
 * Test logger: stores messages so they can be inspected.
 */
class TestLogger : Logger {

    val messages = mutableListOf<String>()

    override val loggerFormatter: LoggerFormatter = object : LoggerFormatter {
        override fun format(text: String, vararg objects: Any?): String = text
    }

    override fun debug(message: String) {
        messages.add("DEBUG $message")
    }

    override fun log(message: String) {
        messages.add("LOG $message")
    }

    override fun info(message: String) {
        messages.add("INFO $message")
    }

    override fun warn(message: String) {
        messages.add("WARN $message")
    }

    override fun error(message: String) {
        messages.add("ERROR $message")
    }

    override fun error(message: String, throwable: Throwable) {
        messages.add("ERROR $message (${throwable.javaClass.simpleName})")
    }

    override fun log(level: LogLevel, message: String) {
        messages.add("${level.name} $message")
    }

    /** All messages, one per line. */
    fun dump(): String = messages.joinToString("\n")
}
