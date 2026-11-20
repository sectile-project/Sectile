package dev.sectile.network.protocol

import dev.sectile.network.api.packet.OutboundPacket
import dev.sectile.network.protocol.interfaceui.ArgCacheInvalidationPacket
import dev.sectile.network.protocol.interfaceui.ArgValuesRequestPacket
import dev.sectile.network.protocol.interfaceui.ArgValuesResponsePacket
import dev.sectile.network.protocol.interfaceui.ChatMessagePacket
import dev.sectile.network.protocol.interfaceui.CommandArgInfo
import dev.sectile.network.protocol.interfaceui.CommandOptionalArgEntry
import dev.sectile.network.protocol.interfaceui.CommandTreeEntry
import dev.sectile.network.protocol.interfaceui.CommandTreeSyncPacket
import dev.sectile.network.protocol.interfaceui.OpenChatWithCommandPacket
import dev.sectile.network.protocol.packet.server.FormattedMessageData
import dev.sectile.network.protocol.packet.server.ServerMessagePacket
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CommandProtocolTest {

    private fun serialize(packet: OutboundPacket): ByteArray {
        val buf = TestWriteBuffer(16384)
        packet.write(buf)
        val bytes = buf.toByteArray()
        assertEquals(packet.size(), bytes.size, "size() mismatch for ${packet.definition.name}")
        return bytes
    }

    @Test
    fun `chat message packet reads inline string with no offset slot`() {
        val buf = TestWriteBuffer(64).apply {
            writeByte(0x01)
            ProtocolCodec.writeVarString(this, "/help", ChatMessagePacket.MAX_MESSAGE_LENGTH)
        }
        val decoded = ChatMessagePacket().apply { read(TestReadBuffer(buf.toByteArray())) }
        assertEquals("/help", decoded.message)

        val decodedEmpty = ChatMessagePacket().apply { read(TestReadBuffer(byteArrayOf(0x00))) }
        assertNull(decodedEmpty.message)
    }

    @Test
    fun `open chat with command round trips cleanly`() {
        val packet = OpenChatWithCommandPacket("/tp ")
        val bytes = serialize(packet)

        val decoded = OpenChatWithCommandPacket().apply { read(TestReadBuffer(bytes)) }
        assertEquals("/tp ", decoded.command)
    }

    @Test
    fun `arg values request reads canonical offset slots`() {
        val fullBuf = TestWriteBuffer(128).apply {
            writeByte(0x03)
            val idSlot = ProtocolCodec.reserveOffset(this)
            val partialSlot = ProtocolCodec.reserveOffset(this)
            val varBase = index
            writeIntAt(idSlot, index - varBase)
            ProtocolCodec.writeVarString(this, "player", ProtocolCodec.MAX_STRING_LENGTH)
            writeIntAt(partialSlot, index - varBase)
            ProtocolCodec.writeVarString(this, "al", ProtocolCodec.MAX_STRING_LENGTH)
        }
        val decodedFull = ArgValuesRequestPacket().apply { read(TestReadBuffer(fullBuf.toByteArray())) }
        assertEquals("player", decodedFull.argTypeId)
        assertEquals("al", decodedFull.partial)

        val partialBuf = TestWriteBuffer(128).apply {
            writeByte(0x02)
            writeInt(-1)
            val partialSlot = ProtocolCodec.reserveOffset(this)
            val varBase = index
            writeIntAt(partialSlot, index - varBase)
            ProtocolCodec.writeVarString(this, "x", ProtocolCodec.MAX_STRING_LENGTH)
        }
        val decodedPartial = ArgValuesRequestPacket().apply { read(TestReadBuffer(partialBuf.toByteArray())) }
        assertNull(decodedPartial.argTypeId)
        assertEquals("x", decodedPartial.partial)
    }

    @Test
    fun `arg values response serializes values continuations and completion flag`() {
        val packet = ArgValuesResponsePacket(
            argTypeId = "world",
            values = listOf("default", "arena"),
            continuations = listOf(false, true),
            isComplete = false,
        )
        val bytes = serialize(packet)

        assertEquals(0x07, bytes[0].toInt() and 0xFF)
        assertEquals(0, bytes[1].toInt() and 0xFF)
        assertEquals(0, readIntLE(bytes, 2))
        assertTrue(readIntLE(bytes, 6) > 0)
        assertTrue(readIntLE(bytes, 10) > readIntLE(bytes, 6))
    }

    @Test
    fun `arg cache invalidation serializes arg type ids`() {
        val packet = ArgCacheInvalidationPacket(listOf("player", "world"))
        val bytes = serialize(packet)

        assertEquals(0x01, bytes[0].toInt() and 0xFF)
        assertEquals(2, bytes[1].toInt() and 0xFF)
    }

    @Test
    fun `command tree sync serializes nested tree without variants or overrides`() {
        val entry = CommandTreeEntry(
            name = "tp",
            aliases = listOf("teleport"),
            usageText = "/tp <player>",
            description = "Teleports a player.",
            subcommands = listOf(
                CommandTreeEntry(
                    name = "spawn",
                    aliases = emptyList(),
                    usageText = "/tp spawn",
                    description = "Teleport to spawn",
                    subcommands = emptyList(),
                    subcommandHints = emptyList(),
                    requiredArgs = emptyList(),
                    optionalArgs = emptyList(),
                ),
            ),
            subcommandHints = listOf(""),
            requiredArgs = listOf(
                CommandArgInfo("player", "player", "Player", 0, "Target player"),
            ),
            optionalArgs = listOf(
                CommandOptionalArgEntry("--silent", "", null, "Suppress feedback"),
            ),
        )

        val packet = CommandTreeSyncPacket(listOf(entry))
        val bytes = serialize(packet)

        assertEquals(0x01, bytes[0].toInt() and 0xFF)
        assertEquals(1, bytes[1].toInt() and 0xFF)
    }

    @Test
    fun `server message packet serializes color int to hex and nested styles cleanly`() {
        val emptyBytes = serialize(ServerMessagePacket(null, chatType = 0))
        assertEquals(2, emptyBytes.size)
        assertEquals(0x00, emptyBytes[0].toInt() and 0xFF)

        val child = object : FormattedMessageData {
            override val rawText: String = "world"
            override val messageId: String? = null
            override val colorRgb: Int = -1
            override val bold: Boolean = false
            override val italic: Boolean = true
            override val monospace: Boolean = false
            override val underline: Boolean = false
            override val strikethrough: Boolean = true
            override val link: String = "https://sectile.dev"
            override val children: List<FormattedMessageData> = emptyList()
        }
        val root = object : FormattedMessageData {
            override val rawText: String = "Hi "
            override val messageId: String? = null
            override val colorRgb: Int = 0x1A2B3C
            override val bold: Boolean = true
            override val italic: Boolean = false
            override val monospace: Boolean = false
            override val underline: Boolean = true
            override val strikethrough: Boolean = false
            override val link: String? = null
            override val children: List<FormattedMessageData> = listOf(child)
        }

        val packet = ServerMessagePacket(root, chatType = 0)
        val bytes = serialize(packet)

        assertEquals(0x01, bytes[0].toInt() and 0xFF)
        assertEquals(0x00, bytes[1].toInt() and 0xFF)
        assertEquals(0xA9, bytes[2].toInt() and 0xFF)
        assertEquals(0x04, bytes[3].toInt() and 0xFF)
        assertEquals(1, bytes[4].toInt() and 0xFF)
        assertEquals(0, bytes[5].toInt() and 0xFF)
        assertEquals(0, bytes[6].toInt() and 0xFF)
        assertEquals(1, bytes[7].toInt() and 0xFF)
        assertEquals(0, bytes[8].toInt() and 0xFF)
        assertEquals(0, bytes[9].toInt() and 0xFF)

        val varBase = 42
        val rawSlot = readIntLE(bytes, 10)
        val idSlot = readIntLE(bytes, 14)
        val childrenSlot = readIntLE(bytes, 18)
        val colorSlot = readIntLE(bytes, 30)
        val linkSlot = readIntLE(bytes, 34)

        assertEquals(0, rawSlot)
        assertEquals(-1, idSlot)
        assertTrue(childrenSlot > 0)
        assertTrue(colorSlot > childrenSlot)
        assertEquals(-1, linkSlot)

        val hexLen = bytes[varBase + colorSlot].toInt() and 0xFF
        assertEquals(7, hexLen)
        assertEquals("#1A2B3C", String(bytes, varBase + colorSlot + 1, 7, Charsets.US_ASCII))
    }

    private fun readIntLE(bytes: ByteArray, offset: Int): Int {
        var value = 0
        for (shift in 0 until 4) {
            value = value or ((bytes[offset + shift].toInt() and 0xFF) shl (shift * 8))
        }
        return value
    }
}
