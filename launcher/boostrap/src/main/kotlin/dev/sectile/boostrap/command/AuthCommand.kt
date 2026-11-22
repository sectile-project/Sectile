package dev.sectile.boostrap.command

import dev.sectile.engine.api.command.Command
import dev.sectile.kernel.Kernel
import dev.sectile.network.internal.auth.NetworkAuth
import dev.sectile.network.internal.auth.ServerAuthenticator
import dev.sectile.sdk.command.command
import dev.sectile.sdk.command.subcommand
import java.time.OffsetDateTime
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max

internal fun authCommand(): Command = command("auth", "Manage Hytale server authentication.") {
    val loginInProgress = AtomicBoolean(false)

    subcommand("login", "Authenticate the server with Hytale.") {
        executesConsole("Only the console can authenticate the server.") { sender, _ ->
            if (!loginInProgress.compareAndSet(false, true)) {
                sender.receiveMessage("Authentication is already in progress.")
                return@executesConsole
            }
            Thread.ofVirtual().name("sectile-auth-login").start {
                try {
                    val logger = Kernel.globalLogger
                    val credentials = ServerAuthenticator.authenticate(logger)
                    if (credentials == null) {
                        sender.receiveMessage("Server authentication failed.")
                    } else {
                        NetworkAuth.updateCredentials(credentials, logger)
                        sender.receiveMessage(
                            "Authenticated as '${credentials.profileName}' (expires in ${minutesUntilExpire(credentials.expiresAt)} minutes).",
                        )
                    }
                } finally {
                    loginInProgress.set(false)
                }
            }
        }
    }

    subcommand("status", "Show current authentication status.") {
        executesConsole("Only the console can inspect server authentication.") { sender, _ ->
            if (NetworkAuth.isAuthenticated) {
                val cached = ServerAuthenticator.load()
                val profile = cached?.profileName ?: "unknown"
                val expires = cached?.expiresAt ?: "unknown"
                sender.receiveMessage("Authenticated as '$profile' (expires in ${minutesUntilExpire(expires)} minutes).")
            } else {
                sender.receiveMessage("Not authenticated. Run '/auth login' to sign in.")
            }
        }
    }
}

private fun minutesUntilExpire(isoTime: String?): Long = if (isoTime == null) {
    0L
} else {
    max(
        0L,
        (OffsetDateTime.parse(isoTime).toInstant().toEpochMilli() - System.currentTimeMillis()) / 60_000L,
    )
}
