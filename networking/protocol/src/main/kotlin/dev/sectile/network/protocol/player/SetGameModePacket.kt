package dev.sectile.network.protocol.player

import dev.sectile.network.api.buffer.WriteBuffer
import dev.sectile.network.api.packet.OutboundPacket
import dev.sectile.network.api.packet.PacketDefinition
import dev.sectile.network.protocol.ProtocolPacketTable
import dev.sectile.network.protocol.enums.GameMode

/**
 * Outbound `SetGameMode` packet (id 101, server -> client).
 *
 * Sets the local player's active [GameMode].
 */
class SetGameModePacket(
    var gameMode: GameMode = GameMode.ADVENTURE,
) : OutboundPacket {

    override val definition: PacketDefinition
        get() = ProtocolPacketTable.SET_GAME_MODE

    override fun write(buf: WriteBuffer) {
        buf.writeByte(gameMode.ordinal)
    }

    override fun size(): Int = ProtocolPacketTable.SET_GAME_MODE.maxSize
}
