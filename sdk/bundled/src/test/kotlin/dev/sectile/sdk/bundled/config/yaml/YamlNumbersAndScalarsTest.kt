package dev.sectile.sdk.bundled.config.yaml

import dev.sectile.kernel.config.ConfigSection
import java.math.BigInteger
import java.nio.charset.StandardCharsets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class YamlNumbersAndScalarsTest {

    @Test
    fun parsesBinaryOctalAndHexadecimalIntegers() {
        val result =
            parse(
                """
                hexLower: 0x2a
                hexUpper: 0XFF
                binary: 0b1010
                binaryUpper: 0B11
                octal: 0o17
                octalUpper: 0O10
                negativeHex: -0x10
                """.trimIndent(),
            )

        assertEquals(42, result.getInt("hexLower"))
        assertEquals(255, result.getInt("hexUpper"))
        assertEquals(10, result.getInt("binary"))
        assertEquals(3, result.getInt("binaryUpper"))
        assertEquals(15, result.getInt("octal"))
        assertEquals(8, result.getInt("octalUpper"))
        assertEquals(-16, result.getInt("negativeHex"))
    }

    @Test
    fun parsesUnderscoredNumbers() {
        val result =
            parse(
                """
                decimal: 1_000_000
                hex: 0xDE_AD_BE_EF
                binary: 0b1010_1010
                octal: 0o1_7
                """.trimIndent(),
            )

        assertEquals(1_000_000, result.getInt("decimal"))
        assertEquals(0xDEADBEEF, result.getLong("hex"))
        assertEquals(170, result.getInt("binary"))
        assertEquals(15, result.getInt("octal"))
    }

    @Test
    fun parsesScientificAndFractionalValues() {
        val result =
            parse(
                """
                sci: 1.5e2
                negativeExp: 2e-3
                upperE: 3E2
                fraction: 0.125
                negativeFraction: -0.5
                """.trimIndent(),
            )

        assertEquals(150.0, result.getDouble("sci"))
        assertEquals(0.002, result.getDouble("negativeExp"))
        assertEquals(300.0, result.getDouble("upperE"))
        assertEquals(0.125, result.getDouble("fraction"))
        assertEquals(-0.5, result.getDouble("negativeFraction"))
    }

    @Test
    fun parsesVeryLargeAndVerySmallNumbers() {
        val result =
            parse(
                """
                huge: 999999999999999999999999999999
                maxLong: 9223372036854775807
                minLong: -9223372036854775808
                tiny: 0.0000000001
                bigExp: 1e30
                minSubnormal: 5e-324
                """.trimIndent(),
            )

        assertEquals(BigInteger("999999999999999999999999999999"), result["huge"])
        assertEquals(Long.MAX_VALUE, result["maxLong"])
        assertEquals(Long.MIN_VALUE, result["minLong"])
        assertEquals(1e-10, result.getDouble("tiny"))
        assertEquals(1e30, result.getDouble("bigExp"))
        assertEquals(Double.MIN_VALUE, result.getDouble("minSubnormal"))
    }

    @Test
    fun parsesSignedAndZeroValues() {
        val result = parse("plus: +42\nminus: -7\nzero: 0\nnegativeZero: -0")

        assertEquals(42, result.getInt("plus"))
        assertEquals(-7, result.getInt("minus"))
        assertEquals(0, result.getInt("zero"))
        assertEquals(0, result.getInt("negativeZero"))
    }

    @Test
    fun parsesQuotedScalarsAndTrailingComments() {
        val result =
            parse(
                """
                single: 'it''s quoted'
                double: "line\nnext"
                commented: 42 # trailing comment
                plain: hola mundo
                """.trimIndent(),
            )

        assertEquals("it's quoted", result.getString("single"))
        assertEquals("line\nnext", result.getString("double"))
        assertEquals(42, result.getInt("commented"))
        assertEquals("hola mundo", result.getString("plain"))
    }

    @Test
    fun parsesFlowCollectionsContainingRadixNumbers() {
        val result = parse("values: [0x10, 0b11, 0o7, 1_000, 1e3, true, null]")

        val values = result["values"] as List<*>
        assertEquals(16, values[0])
        assertEquals(3, values[1])
        assertEquals(7, values[2])
        assertEquals(1000, values[3])
        assertEquals(1000, values[4])
        assertEquals(true, values[5])
        assertEquals(null, values[6])
    }

    @Test
    fun recognizesDocumentMarkersAndSkipsComments() {
        val result =
            parse(
                """
                ---
                # a comment
                key: value
                ...
                """.trimIndent(),
            )

        assertEquals("value", result.getString("key"))
    }

    @Test
    fun parsesLiteralFoldedAndStrippedBlockScalars() {
        val result =
            parse(
                """
                literal: |
                  line1
                  line2
                stripped: |-
                  a
                  b
                folded: >
                  line1
                  line2
                """.trimIndent(),
            )

        assertEquals("line1\nline2\n", result.getString("literal"))
        assertEquals("a\nb", result.getString("stripped"))
        assertEquals("line1 line2\n", result.getString("folded"))
    }

    @Test
    fun rejectsTabIndentation() {
        assertFailsWith<IllegalArgumentException> { parse("key:\n\tvalue") }
    }

    private fun parse(yaml: String): ConfigSection {
        val bytes = yaml.toByteArray(StandardCharsets.UTF_8)
        return StrictYamlParser().parse(bytes, 0, bytes.size)
    }
}
