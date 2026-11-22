package dev.sectile.network.protocol.shared

import dev.sectile.network.api.buffer.ReadBuffer
import dev.sectile.network.api.buffer.WriteBuffer
import dev.sectile.network.protocol.DataSize

class HostAddress(
    var port: Short = 0,
    var host: String = "",
) {

    fun write(buf: WriteBuffer) {
        buf.writeShort(port.toInt())
        buf.writeVarString(host, MAX_HOST_LENGTH)
    }

    fun read(buf: ReadBuffer) {
        port = buf.readShort()
        host = buf.readVarString(MAX_HOST_LENGTH, "Host")
    }

    fun computeSize(): Int = DataSize.SHORT + DataSize.varStringSize(host)

    override fun toString(): String = "$host:$port"

    companion object {
        const val MAX_HOST_LENGTH: Int = 256
        const val MAX_SIZE: Int = 1031
    }
}
