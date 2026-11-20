package dev.sectile.network.protocol.structures

import dev.sectile.network.api.buffer.ReadBuffer
import dev.sectile.network.api.buffer.WriteBuffer
import dev.sectile.network.protocol.ProtocolCodec
import dev.sectile.network.protocol.VarInt

/**
 * Image embedded in a [FormattedMessage].
 *
 * 8 fixed bytes (width and height in little-endian) and the file path as an inline
 * UTF-8 string, with no offset slot (a single variable field).
 */
class FormattedMessageImage(
    var width: Int = 0,
    var height: Int = 0,
    var filePath: String = "",
) {

    fun write(buf: WriteBuffer) {
        buf.writeInt(width)
        buf.writeInt(height)
        ProtocolCodec.writeVarString(buf, filePath, MAX_PATH_LENGTH)
    }

    fun read(buf: ReadBuffer) {
        width = buf.readInt()
        height = buf.readInt()
        filePath = ProtocolCodec.readVarString(buf, MAX_PATH_LENGTH, "FilePath")
    }

    fun computeSize(): Int {
        val bytes = filePath.toByteArray(Charsets.UTF_8).size
        return 8 + VarInt.sizeOf(bytes) + bytes
    }

    companion object {
        const val MAX_PATH_LENGTH: Int = 4_096_000
        const val MAX_SIZE: Int = 16_384_013
    }
}
