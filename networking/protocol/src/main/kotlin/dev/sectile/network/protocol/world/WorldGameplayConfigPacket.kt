package dev.sectile.network.protocol.world

import dev.sectile.network.api.buffer.WriteBuffer
import dev.sectile.network.api.packet.OutboundPacket
import dev.sectile.network.api.packet.PacketDefinition
import dev.sectile.network.protocol.ProtocolPacketTable
import dev.sectile.network.protocol.VarInt
import dev.sectile.network.protocol.structures.CombatConfig
import dev.sectile.network.protocol.structures.PlayerConfig
import dev.sectile.network.protocol.structures.WorldConfig

/**
 * Outbound `WorldGameplayConfig` packet (id 185, server -> client).
 *
 * Sends world gameplay rules: combat configuration, player configuration, and world rules.
 */
class WorldGameplayConfigPacket(
    var combatConfig: CombatConfig = CombatConfig(),
    var playerConfig: PlayerConfig = PlayerConfig(),
    var worldConfig: WorldConfig = WorldConfig(),
) : OutboundPacket {

    override val definition: PacketDefinition
        get() = ProtocolPacketTable.WORLD_GAMEPLAY_CONFIG

    override fun write(buf: WriteBuffer) {
        combatConfig.write(buf)
        playerConfig.write(buf)
        worldConfig.write(buf)
    }

    override fun size(): Int {
        val blockedTagsSize = worldConfig.blockedRootInteractionTagIndexes?.let {
            VarInt.sizeOf(it.size) + it.size * 4
        } ?: 0
        return CombatConfig.MAX_SIZE + PlayerConfig.MAX_SIZE + 7 + blockedTagsSize
    }
}
