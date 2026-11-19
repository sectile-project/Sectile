package dev.sectile.boostrap.console

import dev.sectile.engine.api.command.ConsoleCommandSender
import dev.sectile.kernel.logger.Logger
import java.util.UUID

class ConsoleSender(
    @Volatile
    private var logger: Logger? = null,
) : ConsoleCommandSender {

    override val name: String = "Console"

    override val uuid: UUID = UUID(0L, 0L)

    fun attachLogger(logger: Logger) {
        this.logger = logger
    }

    override fun receiveMessage(message: String) {
        val activeLogger = logger
        if (activeLogger != null) {
            activeLogger.info(message)
        } else {
            println(message)
        }
    }
}
