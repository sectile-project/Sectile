package dev.sectile.sdk.bundled.config.yaml

import dev.sectile.kernel.config.ConfigSection
import org.junit.jupiter.api.assertNull
import java.nio.charset.StandardCharsets
import kotlin.Any
import kotlin.Number
import kotlin.String
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class StrictYamlParserTest {
    private val parser = StrictYamlParser()

    @Test
    fun parsesIntegerAndDecimalNumbers() {
        val result = parse("integer: 42\nnegative: -7\ndecimal: 3.14")

        assertEquals(42, result.getInt("integer"))
        assertEquals(-7, result.getInt("negative"))
        assertEquals(3.14, result.getDouble("decimal"), 0.000001)
    }

    @Test
    fun parsesHexadecimalNumbers() {
        val result = parse("lower: 0x2a\nupper: -0X10")

        assertEquals(42, result.getInt("lower"))
        assertEquals(-16, result.getInt("upper"))
    }

    @Test
    fun parsesQuotedAndPlainStrings() {
        val result = parse(
            "plain: hola mundo\nsingle: 'it''s fine'\ndouble: \"line\\nnext\"",
        )

        assertEquals("hola mundo", result.getString("plain"))
        assertEquals("it's fine", result.getString("single"))
        assertEquals("line\nnext", result.getString("double"))
    }

    @Test
    fun parsesBooleansAndNullValues() {
        val result = parse("enabled: true\ndisabled: false\nempty: null\nother: ~")

        assertEquals(true, result.getBoolean("enabled"))
        assertEquals(false, result.getBoolean("disabled"))
        assertNull(result["empty"])
        assertNull(result["other"])
    }

    @Test
    fun parsesBlockListsAndNestedMaps() {
        val result = parse(
            """
                    items:
                      - first
                      - 2
                      - name: Ada
                        active: true
                    settings:
                      theme: dark
            """.trimIndent(),
        )

        assertEquals(
            listOf<Any?>("first", 2, linkedMapOf<String, Any?>("name" to "Ada", "active" to true)),
            result["items"] as List<Any?>,
        )
        assertEquals(linkedMapOf("theme" to "dark"), result.getSection("settings").data)
    }

    @Test
    fun parsesFlowListsAndMaps() {
        val result = parse("values: [1, \"two\", {enabled: true}]")

        val values = result["values"] as List<Any?>
        assertEquals(1, values[0])
        assertEquals("two", values[1])
        assertEquals(true, (values[2] as ConfigSection).getBoolean("enabled"))
    }

    @Test
    fun parsesJsonObjectAtDocumentRoot() {
        val result = parse(
            "{\"name\":\"Ada\",\"scores\":[10,20],\"profile\":{\"active\":true}}",
        )

        assertEquals("Ada", result.getString("name"))
        assertEquals(mutableListOf(10, 20), result["scores"])
        assertEquals(true, result.getSection("profile").getBoolean("active"))
    }

    @Test
    fun leavesNonNumericValuesAsStrings() {
        val result = parse("version: 1.2.3\naddress: 192.168.1.1")

        assertEquals("1.2.3", result["version"])
        assertEquals("192.168.1.1", result["address"])
        assertFalse(result["version"] is Number)
    }

    private fun parse(yaml: String): ConfigSection {
        val byteArray = yaml.toByteArray(StandardCharsets.UTF_8)
        return parser.parse(byteArray, 0, byteArray.size)
    }
}
