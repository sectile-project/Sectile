package dev.sectile.network.protocol.world.outbound

import dev.sectile.network.api.buffer.WriteBuffer
import dev.sectile.network.api.packet.OutboundPacket
import dev.sectile.network.api.packet.PacketDefinition
import dev.sectile.network.protocol.ProtocolPacketTable
import dev.sectile.network.protocol.player.structures.PlayerConfig
import dev.sectile.network.protocol.world.structures.CombatConfig
import dev.sectile.network.protocol.world.structures.WorldConfig

class WorldGameplayConfigPacket(
    var combatConfig: CombatConfig = CombatConfig(),
    var playerConfig: PlayerConfig = PlayerConfig(),
    var worldConfig: WorldConfig = WorldConfig(),
) : OutboundPacket {

    override val definition: PacketDefinition = ProtocolPacketTable.WORLD_GAMEPLAY_CONFIG

    override fun write(buf: WriteBuffer) {
        combatConfig.write(buf)
        playerConfig.write(buf)
        worldConfig.write(buf)
    }

    override fun size(): Int = CombatConfig.MAX_SIZE + PlayerConfig.MAX_SIZE + worldConfig.computeSize()
}
