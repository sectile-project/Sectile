package dev.sectile.engine.internal

import dev.sectile.engine.api.player.Player
import dev.sectile.engine.api.world.World
import dev.sectile.network.api.player.connection.PlayerConnection
import java.util.UUID

class InternalPlayer(
    override val connection: PlayerConnection,
    override val uuid: UUID,
    override val username: String,
    override var world: World,

    @Volatile
    override var x: Double = 0.0,

    @Volatile
    override var y: Double = 0.0,

    @Volatile
    override var z: Double = 0.0,

    @Volatile
    override var yaw: Float = 0.0F,

    @Volatile
    override var pitch: Float = 0.0F,
) : Player
