package dev.sectile.network.internal.packet.buffer

import dev.sectile.network.api.packet.OutboundPacket
import dev.sectile.network.protocol.auth.AuthGrantPacket
import io.netty.buffer.Unpooled
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The write buffer controls wire layout: if it fails to reserve space for offset slots,
 * the variable block overwrites the slots and the client reads garbage.
 */
class NettyWriteBufferTest {

    private fun write(packet: OutboundPacket): ByteArray {
        val buf = Unpooled.buffer()
        try {
            packet.write(NettyWriteBuffer(buf))
            return ByteArray(buf.readableBytes()).also { buf.readBytes(it) }
        } finally {
            buf.release()
        }
    }

    @Test
    fun `reserves the space of the offset slots`() {
        val bytes = write(AuthGrantPacket("grant", "identity"))

        // nullBits(1) + 2 slots(8) + variable block.
        assertEquals(0x03, bytes[0].toInt() and 0xFF)
        // Slot 1: offset of grant (0).
        assertEquals(0, readIntLE(bytes, 1))
        // Slot 5: offset of identity token (varint(5) + "grant" = 6 bytes).
        assertEquals(6, readIntLE(bytes, 5))
        // Grant starts at offset 9 (right after the slots).
        assertEquals(5, bytes[9].toInt())
        assertEquals("grant", String(bytes, 10, 5, Charsets.US_ASCII))
    }

    @Test
    fun `skip advances the writer index`() {
        val buf = Unpooled.buffer()
        try {
            val writer = NettyWriteBuffer(buf)
            writer.writeByte(0x7F)
            val before = writer.index
            writer.skip(4)
            assertEquals(before + 4, writer.index)
            assertEquals(before + 4, buf.writerIndex())
        } finally {
            buf.release()
        }
    }

    private fun readIntLE(bytes: ByteArray, offset: Int): Int {
        var value = 0
        for (shift in 0 until 4) {
            value = value or ((bytes[offset + shift].toInt() and 0xFF) shl (shift * 8))
        }
        return value
    }
}
