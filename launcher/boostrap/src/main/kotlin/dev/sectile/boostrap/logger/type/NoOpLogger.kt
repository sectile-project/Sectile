package dev.sectile.boostrap.logger.type

import dev.sectile.kernel.logger.DummyLoggerFormatter
import dev.sectile.kernel.logger.Logger

class NoOpLogger : Logger {
    override val loggerFormatter = DummyLoggerFormatter

    override fun debug(message: String) {}
    override fun log(message: String) {}
    override fun info(message: String) {}
    override fun warn(message: String) {}
    override fun error(message: String) {}
    override fun error(message: String, throwable: Throwable) {}
}
