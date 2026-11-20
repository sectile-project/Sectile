package dev.sectile.network.protocol.structures

import dev.sectile.network.api.buffer.ReadBuffer
import dev.sectile.network.api.buffer.WriteBuffer
import dev.sectile.network.protocol.enums.ArmorVisibilityOption

/**
 * World player configuration (`PlayerConfig` of the Hytale protocol).
 *
 * One byte with the armor visibility.
 */
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
        /** Maximum size of the serialized structure. */
        const val MAX_SIZE: Int = 1
    }
}
