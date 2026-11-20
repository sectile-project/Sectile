package dev.sectile.network.protocol.structures

import dev.sectile.network.api.buffer.ReadBuffer
import dev.sectile.network.api.buffer.WriteBuffer
import dev.sectile.network.protocol.ProtocolCodec

/**
 * Host address: port (short in little-endian) and host (UTF-8 string).
 *
 * It has only one variable field, so the host goes inline right after the fixed block
 * (offset 2), with no offset slot.
 */
class HostAddress(
    var port: Short = 0,
    var host: String = "",
) {

    fun write(buf: WriteBuffer) {
        buf.writeShort(port.toInt())
        ProtocolCodec.writeVarString(buf, host, MAX_HOST_LENGTH)
    }

    fun read(buf: ReadBuffer) {
        port = buf.readShort()
        host = ProtocolCodec.readVarString(buf, MAX_HOST_LENGTH, "Host")
    }

    override fun toString(): String = "$host:$port"

    companion object {
        /** Maximum length of the host. */
        const val MAX_HOST_LENGTH: Int = 256

        /** Maximum size of the serialized structure. */
        const val MAX_SIZE: Int = 1031
    }
}
