package dev.sectile.sdk.bundled.config

import java.nio.charset.StandardCharsets
import kotlin.test.Test
import kotlin.test.assertEquals

class ByteReaderTest {

    @Test
    fun resetSkipsUtf8Bom() {
        val bytes = bom() + "abc".ascii()

        val reader = ByteReader(bytes)

        assertEquals(3, reader.pos)
        assertEquals(6, reader.end)
        assertEquals('a'.code.toByte(), reader.next())
    }

    @Test
    fun resetHonorsOffsetAndOnlySkipsBomAtThatOffset() {
        val bytes = "xx".ascii() + bom() + "abc".ascii()

        val reader = ByteReader()
        reader.reset(bytes, 2, 5)

        assertEquals(5, reader.pos)
        assertEquals(7, reader.end)
        assertEquals('a'.code.toByte(), reader.next())
    }

    @Test
    fun resetWithoutBomStartsAtTheRequestedOffset() {
        val reader = ByteReader()
        reader.reset("xxabc".ascii(), 2, 3)

        assertEquals(2, reader.pos)
        assertEquals(5, reader.end)
    }

    @Test
    fun readsLittleEndianWordsAndKeywordConstants() {
        assertEquals(ByteReader.INT_NULL, ByteReader.getIntLE("null".ascii(), 0))
        assertEquals(ByteReader.INT_TRUE, ByteReader.getIntLE("true".ascii(), 0))
        assertEquals(ByteReader.INT_FALS, ByteReader.getIntLE("fals".ascii(), 0))
        assertEquals(0x0807060504030201L, ByteReader.getLongLE(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8), 0))
    }

    @Test
    fun skipJsonWhitespaceConsumesSpacesTabsAndLineBreaks() {
        val reader = ByteReader("\n\t  value".ascii())

        reader.skipJsonWhitespace()

        assertEquals(4, reader.pos)
        assertEquals(2, reader.lineNumber)
        assertEquals('v'.code.toByte(), reader.buf[reader.pos])
    }

    @Test
    fun skipJsonWhitespaceCountsCrLfAsOneLine() {
        val reader = ByteReader("\r\n\r\n  x".ascii())

        reader.skipJsonWhitespace()

        assertEquals(6, reader.pos)
        assertEquals(3, reader.lineNumber)
        assertEquals('x'.code.toByte(), reader.buf[reader.pos])
    }

    @Test
    fun skipJsonWhitespaceLeavesPosUnchangedForVisibleContent() {
        val reader = ByteReader("value".ascii())

        reader.skipJsonWhitespace()

        assertEquals(0, reader.pos)
    }

    @Test
    fun indexOfNewlineFindsFirstLfAndStopsAtEnd() {
        val buf = "ab\ncd\n".ascii()

        assertEquals(2, ByteReader.indexOfNewline(buf, 0, buf.size))
        assertEquals(5, ByteReader.indexOfNewline(buf, 3, buf.size))
        assertEquals(buf.size, ByteReader.indexOfNewline(buf, 6, buf.size))
    }

    @Test
    fun appendUtf8DecodesTwoThreeAndFourByteSequences() {
        val text = "é€😀"
        val reader = ByteReader(text.toByteArray(StandardCharsets.UTF_8))

        while (reader.pos < reader.end) {
            reader.appendUtf8(reader.next())
        }

        assertEquals(text, reader.sb.toString())
    }

    @Test
    fun errorIncludesTheCurrentLineNumber() {
        val reader = ByteReader("x\ny".ascii())
        assertEquals("Parse error (line 1): boom", reader.error("boom").message)

        reader.next() // 'x'
        reader.skipJsonWhitespace() // consumes the '\n' and increments the line counter
        assertEquals("Parse error (line 2): boom", reader.error("boom").message)
    }

    private fun bom(): ByteArray = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
}

private fun String.ascii(): ByteArray = toByteArray(StandardCharsets.ISO_8859_1)
