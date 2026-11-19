package dev.sectile.engine.chat.format

import dev.sectile.engine.chat.color.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChatLegacyTest {

    @Test
    fun `plain text stays raw`() {
        val msg = ChatLegacy.format("Hello world")
        assertTrue(msg.isRaw)
        assertEquals("Hello world", msg.rawText)
        assertEquals(-1, msg.colorRgb)
    }

    @Test
    fun `styles before color carry over to next fragment`() {
        val parts = ChatLegacy.formatToList("&bTest&n&k&l&aOne")
        assertEquals(2, parts.size)

        assertEquals("Test", parts[0].rawText)
        assertEquals(Color.AQUA.rgb, parts[0].colorRgb)
        assertFalse(parts[0].bold)
        assertFalse(parts[0].underline)

        assertEquals("One", parts[1].rawText)
        assertEquals(Color.GREEN.rgb, parts[1].colorRgb)
        assertTrue(parts[1].bold)
        assertTrue(parts[1].underline)
    }

    @Test
    fun `color after non-empty styled text resets previous styles`() {
        val parts = ChatLegacy.formatToList("&b&lTest&aOne")
        assertEquals(2, parts.size)

        assertEquals("Test", parts[0].rawText)
        assertEquals(Color.AQUA.rgb, parts[0].colorRgb)
        assertTrue(parts[0].bold)

        assertEquals("One", parts[1].rawText)
        assertEquals(Color.GREEN.rgb, parts[1].colorRgb)
        assertFalse(parts[1].bold)
    }

    @Test
    fun `supports hex colors reset newlines section char and literal ampersands`() {
        val msg = ChatLegacy.format("&#FF5500Alert&r\n&cRed &z&")
        assertEquals("Alert\nRed &z&", msg.toPlainText())

        val parts = msg.children
        assertEquals(3, parts.size)
        assertEquals(0xFF5500, parts[0].colorRgb)
        assertEquals("Alert", parts[0].rawText)
        assertEquals("\n", parts[1].rawText)
        assertTrue(parts[1].isRaw)
        assertEquals(Color.RED.rgb, parts[2].colorRgb)
        assertEquals("Red &z&", parts[2].rawText)

        val section = ChatLegacy.formatColorChar(ChatLegacy.color("&aHi"))
        assertEquals(Color.GREEN.rgb, section.colorRgb)
        assertEquals("Hi", section.rawText)
    }
}
