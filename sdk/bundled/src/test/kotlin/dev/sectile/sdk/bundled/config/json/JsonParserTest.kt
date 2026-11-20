package dev.sectile.sdk.bundled.config.json

import dev.sectile.kernel.config.ConfigSection
import java.math.BigInteger
import java.nio.charset.StandardCharsets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JsonParserTest {

    @Test
    fun parsesScalarValues() {
        val result = parse("{\"yes\":true,\"no\":false,\"nil\":null,\"text\":\"hello\"}")

        assertEquals(true, result["yes"])
        assertEquals(false, result["no"])
        assertNull(result["nil"])
        assertEquals("hello", result["text"])
    }

    @Test
    fun parsesIntegersAcrossNumericWidths() {
        val result =
            parse(
                "{\"tiny\":7,\"negative\":-42,\"intMax\":2147483647," +
                    "\"long\":2147483648,\"huge\":9223372036854775808}",
            )

        assertTrue(result["tiny"] is Int)
        assertTrue(result["long"] is Long)
        assertTrue(result["huge"] is BigInteger)
        assertEquals(7, result["tiny"])
        assertEquals(-42, result["negative"])
        assertEquals(Int.MAX_VALUE, result["intMax"])
        assertEquals(2147483648L, result["long"])
        assertEquals(BigInteger("9223372036854775808"), result["huge"])
    }

    @Test
    fun parsesFloatingPointNumbers() {
        val result =
            parse("{\"pi\":3.14159,\"sci\":1.5e3,\"small\":1e-9,\"negative\":-0.5,\"min\":5e-324}")

        assertEquals(3.14159, result.getDouble("pi"))
        assertEquals(1500.0, result.getDouble("sci"))
        assertEquals(1e-9, result.getDouble("small"))
        assertEquals(-0.5, result.getDouble("negative"))
        assertEquals(Double.MIN_VALUE, result.getDouble("min"))
    }

    @Test
    fun parsesStringsWithEscapesAndUnicode() {
        val result = parse("{\"escaped\":\"a\\tb\\nc\\\"d\\\\e\\u00e9\\uD83D\\uDE00\"}")

        assertEquals("a\tb\nc\"d\\eé😀", result.getString("escaped"))
    }

    @Test
    fun parsesNestedObjectsAndArrays() {
        val result =
            parse(
                "{\"user\":{\"name\":\"Ada\",\"roles\":[\"admin\",\"dev\"]}," +
                    "\"matrix\":[[1,2],[3,4]]}",
            )

        assertEquals("Ada", result.getSection("user").getString("name"))
        assertEquals(listOf("admin", "dev"), result.getSection("user")["roles"])
        assertEquals(listOf(1, 2), (result["matrix"] as List<*>)[0])
        assertEquals(listOf(3, 4), (result["matrix"] as List<*>)[1])
    }

    @Test
    fun parsesEmptyObjectsAndArrays() {
        val result = parse("{\"object\":{},\"array\":[]}")

        assertTrue(result.getSection("object").data.isEmpty())
        assertTrue((result["array"] as List<*>).isEmpty())
    }

    @Test
    fun acceptsTrailingCommasAsAnExtension() {
        val result = parse("{\"a\":1,\"b\":[1,2,],}")

        assertEquals(1, result["a"])
        assertEquals(listOf(1, 2), result["b"])
    }

    @Test
    fun skipsUtf8BomBeforeTheRootValue() {
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        val json = "{\"a\":1}".toByteArray(StandardCharsets.UTF_8)
        val bytes = bom + json

        val result = JsonParser().parseObject(bytes, 0, bytes.size)

        assertEquals(1, result["a"])
    }

    @Test
    fun wrapsARootArrayUnderTheEmptyKey() {
        val result = parse("[1,\"two\",true]")

        assertEquals(listOf(1, "two", true), result[""])
    }

    @Test
    fun rejectsMalformedNumbers() {
        assertFailsWith<IllegalArgumentException> { parse("{\"n\":01}") }
        assertFailsWith<IllegalArgumentException> { parse("{\"n\":1.}") }
        assertFailsWith<IllegalArgumentException> { parse("{\"n\":1e400}") }
    }

    @Test
    fun rejectsMissingOrTrailingContent() {
        assertFailsWith<IllegalArgumentException> { parse("") }
        assertFailsWith<IllegalArgumentException> { parse("{\"a\":1} extra") }
        assertFailsWith<IllegalArgumentException> { parse("42") }
        assertFailsWith<IllegalArgumentException> { parse("\"a string\"") }
    }

    @Test
    fun rejectsInvalidStrings() {
        assertFailsWith<IllegalArgumentException> { parse("{\"a\":\"line\nbreak\"}") }
        assertFailsWith<IllegalArgumentException> { parse("{\"a\":\"\\q\"}") }
        assertFailsWith<IllegalArgumentException> { parse("{\"a\":\"\\x41\"}") }
    }

    @Test
    fun rejectsMalformedStructure() {
        assertFailsWith<IllegalArgumentException> { parse("{\"a\" 1}") }
        assertFailsWith<IllegalArgumentException> { parse("{\"a\":1") }
        assertFailsWith<IllegalArgumentException> { parse("{a:1}") }
    }
}

private fun parse(json: String): ConfigSection {
    val bytes = json.toByteArray(StandardCharsets.UTF_8)
    return JsonParser().parseObject(bytes, 0, bytes.size)
}
