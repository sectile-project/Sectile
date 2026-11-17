package dev.sectile.network.internal.stages

import dev.sectile.kernel.logger.Logger
import dev.sectile.network.internal.config.NetworkConfig
import java.time.Duration
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Server wide environment shared by every stage and every step.
 *
 * The environment holds the pieces that are the same for all the connections: the server
 * configuration, the logger, the executor used for the blocking authentication work and the
 * counter that assigns the entity ids to the players.
 *
 * @param config server configuration.
 * @param logger server logger.
 * @param authExecutor executor used for the blocking authentication work (HTTP calls to the
 *   Session Service and JWT validation), so the connection event loop is never blocked.
 */
internal class StageEnvironment(
    val config: NetworkConfig,
    val logger: Logger,
    val authExecutor: ExecutorService,
) {

    /** Entity id given to the next player that completes the login. */
    private val entityIds = AtomicInteger(1)

    /** How long a player may take to load the world before the connection is closed. */
    val playTimeout: Duration
        get() = Duration.ofSeconds(config.playTimeoutSeconds.toLong())

    /** Returns the entity id of the next player that joins the server. */
    fun nextEntityId(): Int = entityIds.getAndIncrement()

    companion object {

        /**
         * Creates the environment and the executor used by the authentication steps.
         */
        fun create(config: NetworkConfig, logger: Logger): StageEnvironment = StageEnvironment(
            config = config,
            logger = logger,
            authExecutor = Executors.newCachedThreadPool { runnable ->
                Thread(runnable, "sectile-stage-auth").apply { isDaemon = true }
            },
        )
    }
}
