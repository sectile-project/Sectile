package dev.sectile.sdk.bundled.config

import java.nio.charset.StandardCharsets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class StringDecodeUtilTest {

    @Test
    fun internAsciiKeyCachesShortKeys() {
        val buf = "name:value padding".ascii()

        val first = StringDecodeUtil.internAsciiKey(buf, 0, 4)
        val second = StringDecodeUtil.internAsciiKey(buf, 0, 4)

        assertEquals("name", first)
        assertSame(first, second)
    }

    @Test
    fun internAsciiKeyCachesMediumKeys() {
        val buf = "longKeyName:value padding".ascii()

        assertEquals("longKeyName", StringDecodeUtil.internAsciiKey(buf, 0, 11))
        assertSame(
            StringDecodeUtil.internAsciiKey(buf, 0, 11),
            StringDecodeUtil.internAsciiKey(buf, 0, 11),
        )
    }

    @Test
    fun internAsciiKeyHandlesKeysLongerThanTheCacheWords() {
        val buf = "abcdefghijklmnopqrst:1".ascii()

        assertEquals("abcdefghijklmnopqrst", StringDecodeUtil.internAsciiKey(buf, 0, 20))
    }

    @Test
    fun decodeSliceChoosesAsciiOrUtf8BasedOnTheBytes() {
        val ascii = "hello world".ascii()
        assertEquals("hello world", StringDecodeUtil.decodeSlice(ascii, 0, ascii.size))

        val utf8 = "café 😀".toByteArray(StandardCharsets.UTF_8)
        assertEquals("café 😀", StringDecodeUtil.decodeSlice(utf8, 0, utf8.size))
    }

    @Test
    fun readDoubleQuotedDecodesStandardJsonEscapes() {
        val json = "\"a\\tb\\nc\\\"d\\\\e\\/f\\u0041\""
        val reader = ByteReader(json.ascii())

        assertEquals("a\tb\nc\"d\\e/fA", StringDecodeUtil.readDoubleQuoted(reader, false, true))
        assertEquals(json.length, reader.pos)
    }

    @Test
    fun readDoubleQuotedDecodesSurrogatePairs() {
        val json = "\"\\uD83D\\uDE00\""
        val reader = ByteReader(json.ascii())

        assertEquals("😀", StringDecodeUtil.readDoubleQuoted(reader, false, true))
    }

    @Test
    fun readDoubleQuotedAllowsYamlOnlyEscapesOutsideStrictJson() {
        val json = "\"a\\x41\\e\\0\\U0001F600\""
        val reader = ByteReader(json.ascii())

        assertEquals("aA\u001B\u0000😀", StringDecodeUtil.readDoubleQuoted(reader, false, false))
    }

    @Test
    fun readDoubleQuotedRejectsNonStandardEscapesInStrictJson() {
        val reader = ByteReader("\"\\x41\"".ascii())

        assertFailsWith<IllegalArgumentException> {
            StringDecodeUtil.readDoubleQuoted(reader, false, true)
        }
    }

    @Test
    fun readDoubleQuotedRejectsRawControlCharactersInStrictJson() {
        val json = "\"line\nbreak\""
        val reader = ByteReader(json.ascii())

        assertFailsWith<IllegalArgumentException> {
            StringDecodeUtil.readDoubleQuoted(reader, false, true)
        }
    }

    @Test
    fun readHexEscapeParsesBothCases() {
        val reader = ByteReader("00aF".ascii())

        assertEquals(0x00AF, StringDecodeUtil.readHexEscape(reader, 4))
        assertEquals(4, reader.pos)
    }

    @Test
    fun readHexEscapeRejectsInvalidDigits() {
        val reader = ByteReader("00g1".ascii())

        assertFailsWith<IllegalArgumentException> {
            StringDecodeUtil.readHexEscape(reader, 4)
        }
    }

    @Test
    fun readJsonKeyAndColonSkipsWhitespaceAfterTheColon() {
        val json = "\"name\"  :  42"
        val reader = ByteReader(json.ascii())

        assertEquals("name", StringDecodeUtil.readJsonKeyAndColon(reader))
        assertEquals('4'.code.toByte(), reader.buf[reader.pos])
    }

    @Test
    fun readJsonKeyAndColonUsesTheFastPathForEightByteKeys() {
        val json = "\"abcdefgh\":1" + "                "
        val reader = ByteReader(json.ascii())

        assertEquals("abcdefgh", StringDecodeUtil.readJsonKeyAndColon(reader))
        assertEquals('1'.code.toByte(), reader.buf[reader.pos])
    }

    @Test
    fun readJsonKeyAndColonRequiresAColon() {
        val reader = ByteReader("\"a\" 1".ascii())

        assertFailsWith<IllegalArgumentException> {
            StringDecodeUtil.readJsonKeyAndColon(reader)
        }
    }
}

private fun String.ascii(): ByteArray = toByteArray(StandardCharsets.ISO_8859_1)
