package dev.sectile.engine.internal

import dev.sectile.engine.api.permission.PermissionService
import dev.sectile.engine.api.player.Player
import dev.sectile.engine.api.world.World
import dev.sectile.kernel.Kernel
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
) : Player {

    override fun hasPermission(permission: String): Boolean {
        if (Kernel.isInitialized()) {
            val permissions = Kernel.services[PermissionService::class.java]
            if (permissions != null) {
                return permissions.hasPermission(this, permission)
            }
        }
        return super.hasPermission(permission)
    }
}
