package dev.sectile.kernel.logger

import dev.sectile.kernel.Kernel

class LoggerBuilder(
    private val factory: LoggerFactory = Kernel.loggerFactory,
) {
    var prefix: String = ""
    var suffix: String = ""
    var formatter: LoggerFormatter? = null

    fun withPrefix(prefix: String): LoggerBuilder {
        this.prefix = prefix
        return this
    }

    fun withSuffix(suffix: String): LoggerBuilder {
        this.suffix = suffix
        return this
    }

    fun withFormatter(formatter: LoggerFormatter): LoggerBuilder {
        this.formatter = formatter
        return this
    }

    fun build(): Logger = factory.createLogger(this)
}
