package dev.sectile.engine.api.player

import dev.sectile.engine.api.command.CommandSender
import dev.sectile.engine.api.world.World
import dev.sectile.network.api.player.connection.PlayerConnection
import dev.sectile.network.protocol.packet.server.RawMessagePacket
import java.util.UUID

/**
 * A player inside the server.
 *
 * This is a read-only contract: the mutable implementation lives in `engine:internal`, which is
 * not visible to plugins. A plugin can observe a player but cannot change its world, its
 * position or its connection state through this interface.
 */
interface Player : CommandSender {

    /** Transport handle of this player. Connection concerns only, never game state. */
    val connection: PlayerConnection

    /** World this player currently belongs to. */
    val world: World

    /** Unique player identifier, from the login session. */
    override val uuid: UUID

    /** Username of the player, from the login session. */
    val username: String

    override val name: String
        get() = username

    override fun receiveMessage(message: String) {
        connection.sendPacket(RawMessagePacket(rawText = message))
    }

    /** Position **/
    val x: Double
    val y: Double
    val z: Double

    val yaw: Float
    val pitch: Float
}
