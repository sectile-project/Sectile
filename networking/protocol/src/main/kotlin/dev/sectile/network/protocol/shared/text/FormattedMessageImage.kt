package dev.sectile.network.protocol.shared.text

import dev.sectile.network.api.buffer.ReadBuffer
import dev.sectile.network.api.buffer.WriteBuffer
import dev.sectile.network.protocol.DataSize

class FormattedMessageImage(
    var width: Int = 0,
    var height: Int = 0,
    var filePath: String = "",
) {

    fun write(buf: WriteBuffer) {
        buf.writeInt(width)
        buf.writeInt(height)
        buf.writeVarString(filePath, MAX_PATH_LENGTH)
    }

    fun read(buf: ReadBuffer) {
        width = buf.readInt()
        height = buf.readInt()
        filePath = buf.readVarString(MAX_PATH_LENGTH, "FilePath")
    }

    fun computeSize(): Int = DataSize.INT * 2 + DataSize.varStringSize(filePath)

    companion object {
        const val MAX_PATH_LENGTH: Int = DataSize.MAX_STRING_LENGTH
        const val MAX_SIZE: Int = 16_384_013
    }
}
