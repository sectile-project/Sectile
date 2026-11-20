package dev.sectile.network.protocol.structures

import dev.sectile.network.api.buffer.ReadBuffer
import dev.sectile.network.api.buffer.WriteBuffer

/**
 * Point in time (`java.time.Instant` in the protocol).
 *
 * 12 bytes: seconds as a long in little-endian and nanoseconds as an int in little-endian.
 */
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
        /** Fixed size of the structure. */
        const val SIZE: Int = 12
    }
}
