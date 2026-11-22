package dev.sectile.network.protocol.asset.structures

import dev.sectile.network.api.buffer.ReadBuffer
import dev.sectile.network.api.buffer.WriteBuffer
import dev.sectile.network.protocol.DataSize

class Asset(
    var hash: String = "",
    var name: String = "",
) {

    fun write(buf: WriteBuffer) {
        buf.writeFixedAscii(hash, HASH_LENGTH)
        buf.writeVarString(name, MAX_NAME_LENGTH)
    }

    fun read(buf: ReadBuffer) {
        hash = buf.readFixedAscii(HASH_LENGTH)
        name = buf.readVarString(MAX_NAME_LENGTH, "Name")
    }

    fun computeSize(): Int = HASH_LENGTH + DataSize.varStringSize(name)

    companion object {
        const val HASH_LENGTH: Int = 64
        const val MAX_NAME_LENGTH: Int = 512
        const val MAX_SIZE: Int = 2117
    }
}
