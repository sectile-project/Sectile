package dev.sectile.microkernel.plugin.manifest

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream

/** Tests of the parser used for the manifest bundled inside plugin jars. */
class CustomPropertiesParserTest {

    @Test
    fun `every property is parsed, not only the first one`() {
        // Regression: the old parser skipped the first character of every property after the
        // first line, so "id" became "d" and the descriptor was rejected at runtime.
        val manifest =
            """
            main=dev.sectile.plugin.debug.DebugPlugin
            id=sectile-debug
            version=1.0.0-ALPHA
            boostrap=dev.sectile.plugin.debug.DebugPluginBootstrap
            """.trimIndent()

        val properties = CustomPropertiesParser.parse(manifest.byteInputStream())

        assertEquals("dev.sectile.plugin.debug.DebugPlugin", properties["main"])
        assertEquals("sectile-debug", properties["id"])
        assertEquals("1.0.0-ALPHA", properties["version"])
        assertEquals("dev.sectile.plugin.debug.DebugPluginBootstrap", properties["boostrap"])
    }

    @Test
    fun `crlf line endings comments and blanks are handled`() {
        val manifest = "# comment\r\n\r\n  id = my-plugin  \r\n! another comment\r\nversion=1.0.0\r\n"

        val properties = CustomPropertiesParser.parse(ByteArrayInputStream(manifest.toByteArray()))

        assertEquals(2, properties.size)
        assertEquals("my-plugin", properties["id"])
        assertEquals("1.0.0", properties["version"])
    }

    @Test
    fun `a line without a separator is rejected`() {
        val manifest = "id=my-plugin\nbroken-line\n"

        assertThrows(IllegalArgumentException::class.java) {
            CustomPropertiesParser.parse(manifest.byteInputStream())
        }
    }
}
