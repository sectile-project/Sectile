package dev.sectile.gradle.scanner

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import java.nio.file.Files
import java.nio.file.Path

/** Tests of the package discovery performed by [PluginClassScanner]. */
class PluginClassScannerTest {

    private val scanner =
        PluginClassScanner(
            pluginAnnotationClass = "dev.sectile.sdk.plugin.annotation.Plugin",
            bootstrapAnnotationClass = "dev.sectile.sdk.plugin.annotation.Bootstrap",
            bootstrapAnnotationAliasClass = "dev.sectile.sdk.plugin.annotation.Boostrap",
        )

    @Test
    fun `discovery walks the packages until it finds the plugin`() {
        val root = Files.createTempDirectory("sectile-discovery")
        try {
            // Classes in shallower packages have no annotation.
            writeClass(root, "com/example/Root.class", withPlugin = false)
            writeClass(root, "com/example/deep/Deep.class", withPlugin = false)

            // The plugin is deeper in the tree, together with its bootstrap.
            writeClass(root, "com/example/deep/plugin/MyPlugin.class", withPlugin = true)
            writeClass(root, "com/example/deep/plugin/MyBootstrap.class", withBootstrap = true)

            val result = scanner.discover(listOf(root.toFile()))

            assertEquals("com.example.deep.plugin", result.packageName)
            assertEquals("com.example.deep.plugin.MyPlugin", result.pluginClass?.className)
            assertEquals("com.example.deep.plugin.MyBootstrap", result.bootstrapClass?.className)
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun `discovery returns no plugin when no class is annotated`() {
        val root = Files.createTempDirectory("sectile-discovery")
        try {
            writeClass(root, "com/example/Root.class", withPlugin = false)

            val result = scanner.discover(listOf(root.toFile()))

            assertNull(result.pluginClass)
            assertNull(result.packageName)
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun `an explicit package still works`() {
        val root = Files.createTempDirectory("sectile-discovery")
        try {
            writeClass(root, "com/example/other/MyPlugin.class", withPlugin = true)
            writeClass(root, "com/example/deep/Deep.class", withPlugin = true)

            val result = scanner.scan(listOf(root.toFile()), "com.example.other", false)

            assertNotNull(result.pluginClass)
            assertEquals("com.example.other.MyPlugin", result.pluginClass?.className)
            assertEquals(1, result.allPluginClasses.size)
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    /** Writes a minimal class file, optionally carrying the plugin or bootstrap annotation. */
    private fun writeClass(
        root: Path,
        relativePath: String,
        withPlugin: Boolean = false,
        withBootstrap: Boolean = false,
    ) {
        val writer = ClassWriter(0)
        writer.visit(
            Opcodes.V17,
            Opcodes.ACC_PUBLIC,
            relativePath.removeSuffix(".class").replace('/', '.'),
            null,
            "java/lang/Object",
            null,
        )

        if (withPlugin) {
            val annotation = writer.visitAnnotation("Ldev/sectile/sdk/plugin/annotation/Plugin;", true)
            annotation.visit("id", "test-plugin")
            annotation.visit("version", "1.0.0")
            annotation.visitEnd()
        }

        if (withBootstrap) {
            writer.visitAnnotation("Ldev/sectile/sdk/plugin/annotation/Bootstrap;", true).visitEnd()
        }

        writer.visitEnd()

        val file = root.resolve(relativePath)
        Files.createDirectories(file.parent)
        Files.write(file, writer.toByteArray())
    }
}
