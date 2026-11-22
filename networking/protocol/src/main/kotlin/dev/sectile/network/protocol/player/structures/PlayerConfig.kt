package dev.sectile.network.protocol.player.structures

import dev.sectile.network.api.buffer.ReadBuffer
import dev.sectile.network.api.buffer.WriteBuffer
import dev.sectile.network.protocol.DataSize
import dev.sectile.network.protocol.player.enums.ArmorVisibilityOption

class PlayerConfig(
    var armorVisibility: ArmorVisibilityOption = ArmorVisibilityOption.ALL,
) {

    fun write(buf: WriteBuffer) {
        buf.writeByte(armorVisibility.id)
    }

    fun read(buf: ReadBuffer) {
        armorVisibility = ArmorVisibilityOption.of(buf.readUnsignedByte()) ?: ArmorVisibilityOption.ALL
    }

    companion object {
        const val MAX_SIZE: Int = DataSize.BYTE
    }
}
