package dev.sectile.kernel.logger

import java.util.*

interface LoggerFormatter {
    /**
     * Format a string replacing placeholders, example:
     * `format("hello %s", "world")`
     */
    fun format(
        text: String,
        vararg objects: Any?,
    ): String
}

object DummyLoggerFormatter : LoggerFormatter {
    override fun format(text: String, vararg objects: Any?): String = text
}

object JavaLoggerFormatter : LoggerFormatter {
    val formatter: Formatter = Formatter()
    override fun format(text: String, vararg objects: Any?): String = if (objects.isEmpty()) text else formatter.format(text, *objects).toString()
}
