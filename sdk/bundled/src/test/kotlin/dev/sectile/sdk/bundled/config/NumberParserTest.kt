package dev.sectile.sdk.bundled.config

import java.math.BigInteger
import java.nio.charset.StandardCharsets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NumberParserTest {

    @Test
    fun digit2ParsesTwoDecimalDigitsAtOnce() {
        val buf = "42xy".ascii()
        assertEquals(42, NumberParser.digit2(buf, 0))
        assertEquals(7, NumberParser.digit2("07ab".ascii(), 0))
    }

    @Test
    fun digit2ReturnsMinusOneForNonDigits() {
        val buf = "42xy".ascii()
        assertEquals(-1, NumberParser.digit2(buf, 2))
    }

    @Test
    fun toDoubleUsesTheNegativeMantissaConvention() {
        assertEquals(123.0, NumberParser.toDouble(-123, 0, false))
        assertEquals(-123.0, NumberParser.toDouble(-123, 0, true))
        assertEquals(1.23, NumberParser.toDouble(-123, 2, false))
        assertEquals(12300.0, NumberParser.toDouble(-123, -2, false))
    }

    @Test
    fun toDoublePreservesSignedZero() {
        val positiveZero = NumberParser.toDouble(0, 0, false)
        val negativeZero = NumberParser.toDouble(0, 0, true)

        assertEquals(0.0, positiveZero)
        assertEquals(-0.0, negativeZero)
        assertEquals(0L, java.lang.Double.doubleToRawLongBits(positiveZero))
        assertEquals(
            java.lang.Double.doubleToRawLongBits(-0.0),
            java.lang.Double.doubleToRawLongBits(negativeZero),
        )
    }

    @Test
    fun toDoubleHandlesSubnormalsAndHugeMagnitudes() {
        // 1e-324 is below half of Double.MIN_VALUE and rounds to zero; 5e-324 rounds up to it.
        assertEquals(0.0, NumberParser.toDouble(-1, 324, false))
        assertEquals(Double.MIN_VALUE, NumberParser.toDouble(-5, 324, false))
        assertEquals(1e308, NumberParser.toDouble(-1, -308, false))
    }

    @Test
    fun toCompactIntegerReturnsTheNarrowestFittingType() {
        assertEquals(1, NumberParser.toCompactInteger(-1, false))
        assertEquals(-1, NumberParser.toCompactInteger(-1, true))
        assertEquals(Int.MAX_VALUE, NumberParser.toCompactInteger(-Int.MAX_VALUE.toLong(), false))
        assertEquals(2147483648L, NumberParser.toCompactInteger(-2147483648L, false))
        assertEquals(Int.MIN_VALUE, NumberParser.toCompactInteger(Int.MIN_VALUE.toLong(), true))
        assertEquals(Long.MIN_VALUE, NumberParser.toCompactInteger(Long.MIN_VALUE, true))
    }

    @Test
    fun readJsonNumberReturnsIntegerLongOrBigIntegerByMagnitude() {
        assertTrue(jsonNumber("7") is Int)
        assertTrue(jsonNumber("-42") is Int)
        assertTrue(jsonNumber("2147483648") is Long)
        assertTrue(jsonNumber("9223372036854775808") is BigInteger)

        assertEquals(7, jsonNumber("7"))
        assertEquals(-42, jsonNumber("-42"))
        assertEquals(2147483648L, jsonNumber("2147483648"))
        assertEquals(BigInteger("9223372036854775808"), jsonNumber("9223372036854775808"))
    }

    @Test
    fun readJsonNumberParsesFractionsAndExponentsAsDouble() {
        assertEquals(3.14, jsonNumber("3.14"))
        assertEquals(1000.0, jsonNumber("1e3"))
        assertEquals(0.001, jsonNumber("1e-3"))
        assertEquals(1e-9, jsonNumber("1e-9"))
        assertEquals(Double.MIN_VALUE, jsonNumber("5e-324"))
    }

    @Test
    fun readJsonNumberRejectsNonRfc8259Syntax() {
        assertFailsWith<IllegalArgumentException> { jsonNumber("01") }
        assertFailsWith<IllegalArgumentException> { jsonNumber("1.") }
        assertFailsWith<IllegalArgumentException> { jsonNumber("1e") }
        assertFailsWith<IllegalArgumentException> { jsonNumber("-") }
        assertFailsWith<IllegalArgumentException> { jsonNumber("1e400") }
    }

    @Test
    fun tryParseNumberSupportsDecimalRadixAndUnderscoreForms() {
        assertEquals(42, yamlNumber("42"))
        assertEquals(-42, yamlNumber("-42"))
        assertEquals(1_000_000, yamlNumber("1_000_000"))
        assertEquals(255, yamlNumber("0xff"))
        assertEquals(255, yamlNumber("0XFF"))
        assertEquals(10, yamlNumber("0b1010"))
        assertEquals(10, yamlNumber("0B1010"))
        assertEquals(15, yamlNumber("0o17"))
        assertEquals(15, yamlNumber("0O17"))
        assertEquals(-16, yamlNumber("-0x10"))
        assertEquals(65535, yamlNumber("0xff_ff"))
        assertEquals(170, yamlNumber("0b1010_1010"))
    }

    @Test
    fun tryParseNumberSupportsScientificNotation() {
        assertEquals(1000, yamlNumber("1e3"))
        assertEquals(150.0, yamlNumber("1.5e2"))
        assertEquals(0.002, yamlNumber("2e-3"))
        // Positive exponents that still fit an integer are compacted to Int.
        assertEquals(300, yamlNumber("3E2"))
    }

    @Test
    fun tryParseNumberHandlesLongBoundariesAndBeyond() {
        assertEquals(Long.MAX_VALUE, yamlNumber("9223372036854775807"))
        assertEquals(Long.MIN_VALUE, yamlNumber("-9223372036854775808"))
        assertEquals(BigInteger("9223372036854775808"), yamlNumber("9223372036854775808"))
        assertEquals(
            BigInteger("999999999999999999999999999999"),
            yamlNumber("999999999999999999999999999999"),
        )
    }

    @Test
    fun tryParseNumberHandlesVerySmallMagnitudes() {
        assertEquals(1e-10, yamlNumber("0.0000000001"))
        assertEquals(1e30, yamlNumber("1e30"))
        assertEquals(Double.MIN_VALUE, yamlNumber("5e-324"))
    }

    @Test
    fun tryParseNumberReturnsNullForMalformedInput() {
        assertNull(yamlNumberOrNull(""))
        assertNull(yamlNumberOrNull("-"))
        assertNull(yamlNumberOrNull("--5"))
        assertNull(yamlNumberOrNull("0x"))
        assertNull(yamlNumberOrNull("0b"))
        assertNull(yamlNumberOrNull("0o"))
        assertNull(yamlNumberOrNull("0b2"))
        assertNull(yamlNumberOrNull("0xg"))
        assertNull(yamlNumberOrNull("12a"))
        assertNull(yamlNumberOrNull("1.2.3"))
        assertNull(yamlNumberOrNull("_5"))
        assertNull(yamlNumberOrNull("5_"))
        assertNull(yamlNumberOrNull("1_000_"))
        assertNull(yamlNumberOrNull("1e_3"))
        assertNull(yamlNumberOrNull("1e"))
    }

    private fun jsonNumber(text: String): Number {
        val buf = text.ascii()
        val reader = ByteReader(buf)
        val value = NumberParser.readJsonNumber(reader)
        assertEquals(buf.size, reader.pos)
        return value
    }

    private fun yamlNumber(text: String): Number = yamlNumberOrNull(text) ?: error("Expected '$text' to be parsed as a number")

    private fun yamlNumberOrNull(text: String): Number? {
        val buf = text.ascii()
        return NumberParser.tryParseNumber(buf, 0, buf.size)
    }
}

private fun String.ascii(): ByteArray = toByteArray(StandardCharsets.ISO_8859_1)
