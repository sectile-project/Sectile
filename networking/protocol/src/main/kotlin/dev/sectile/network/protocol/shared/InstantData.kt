package dev.sectile.network.protocol.shared

import dev.sectile.network.api.buffer.ReadBuffer
import dev.sectile.network.api.buffer.WriteBuffer
import dev.sectile.network.protocol.DataSize

class InstantData(
    var seconds: Long = 0L,
    var nanos: Int = 0,
) {

    fun write(buf: WriteBuffer) {
        buf.writeLong(seconds)
        buf.writeInt(nanos)
    }

    fun read(buf: ReadBuffer) {
        seconds = buf.readLong()
        nanos = buf.readInt()
    }

    override fun toString(): String = "InstantData(seconds=$seconds, nanos=$nanos)"

    companion object {
        const val SIZE: Int = DataSize.LONG + DataSize.INT
    }
}
