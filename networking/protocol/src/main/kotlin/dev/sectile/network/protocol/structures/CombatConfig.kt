package dev.sectile.network.protocol.structures

import dev.sectile.network.api.buffer.ReadBuffer
import dev.sectile.network.api.buffer.WriteBuffer
import dev.sectile.network.protocol.ProtocolCodec
import dev.sectile.network.protocol.VarInt

/**
 * World combat configuration (`CombatConfig` of the Hytale protocol).
 *
 * One byte with the bits of the options that are active.
 */
class CombatConfig(
    var displayHealthBars: Boolean = true,
    var displayCombatText: Boolean = true,
) {

    fun write(buf: WriteBuffer) {
        var bits = 0
        if (displayHealthBars) bits = bits or 0x01
        if (displayCombatText) bits = bits or 0x02
        buf.writeByte(bits)
    }

    fun read(buf: ReadBuffer) {
        val bits = buf.readUnsignedByte()
        displayHealthBars = bits and 0x01 != 0
        displayCombatText = bits and 0x02 != 0
    }

    companion object {
        /** Maximum size of the serialized structure. */
        const val MAX_SIZE: Int = 1
    }
}
