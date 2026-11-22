package dev.sectile.network.protocol.player.outbound

import dev.sectile.network.api.buffer.WriteBuffer
import dev.sectile.network.api.packet.OutboundPacket
import dev.sectile.network.api.packet.PacketDefinition
import dev.sectile.network.protocol.ProtocolPacketTable
import dev.sectile.network.protocol.player.enums.GameMode

class SetGameModePacket(
    var gameMode: GameMode = GameMode.ADVENTURE,
) : OutboundPacket {

    override val definition: PacketDefinition = ProtocolPacketTable.SET_GAME_MODE

    override fun write(buf: WriteBuffer) {
        buf.writeByte(gameMode.ordinal)
    }

    override fun size(): Int = ProtocolPacketTable.SET_GAME_MODE.maxSize
}
