package dev.sectile.gradle.scanner

import dev.sectile.gradle.model.ScanResult
import dev.sectile.gradle.model.ScannedClassInfo
import dev.sectile.gradle.scanner.asm.PluginClassAnnotationVisitor
import org.objectweb.asm.ClassReader
import java.io.File

class PluginClassScanner(
    private val pluginAnnotationClass: String,
    private val bootstrapAnnotationClass: String,
    private val bootstrapAnnotationAliasClass: String,
) {

    /**
     * Scans a single package, optionally including its subpackages.
     *
     * Use this when the package is configured explicitly in the build script.
     */
    fun scan(
        classRoots: Collection<File>,
        targetPackage: String,
        includeSubpackages: Boolean,
    ): ScanResult {
        val scanned =
            scanAll(classRoots).filter {
                isInsideTargetPackage(
                    classPackage = it.packageName,
                    targetPackage = targetPackage,
                    includeSubpackages = includeSubpackages,
                )
            }

        return toResult(scanned, targetPackage)
    }

    /**
     * Walks the packages of the project from the shallowest to the deepest and stops at the
     * first one that contains a class annotated with `@Plugin`.
     *
     * The packages are inferred from the compiled classes of the project, so the plugin author
     * does not have to configure a package name. Only that exact package is checked at each
     * step: the walk descends into a subpackage when the parent has no plugin.
     *
     * The bootstrap is looked up in the plugin's own package first, then in its subpackages,
     * then anywhere in the project.
     */
    fun discover(classRoots: Collection<File>): ScanResult {
        val scanned = scanAll(classRoots)

        // Depth first: a package with fewer segments is always checked before its children.
        val packages =
            scanned
                .map { it.packageName }
                .filter { it.isNotEmpty() }
                .distinct()
                .sortedWith(compareBy<String> { it.count { char -> char == '.' } }.thenBy { it })

        val allPlugins = scanned.filter { it.hasPlugin }

        for (candidate in packages) {
            val plugin = allPlugins.firstOrNull { it.packageName == candidate } ?: continue

            val bootstrap =
                scanned.firstOrNull { it.hasBootstrap && it.packageName == candidate }
                    ?: scanned.firstOrNull { it.hasBootstrap && it.packageName.startsWith("$candidate.") }
                    ?: scanned.firstOrNull { it.hasBootstrap }

            return ScanResult(plugin, bootstrap, allPlugins, candidate)
        }

        return ScanResult(null, null, allPlugins, null)
    }

    /** Reads and inspects every compiled class of the project once. */
    private fun scanAll(classRoots: Collection<File>): List<ScannedClassInfo> =
        classRoots
            .filter { it.exists() }
            .flatMap { root ->
                if (root.isDirectory) {
                    root.walkTopDown()
                        .filter { it.isFile && it.extension == "class" }
                        .toList()
                } else {
                    emptyList()
                }
            }
            .sortedBy { it.absolutePath }
            .map { scanClass(it) }

    private fun scanClass(classFile: File): ScannedClassInfo {
        val reader = ClassReader(classFile.readBytes())
        val visitor =
            PluginClassAnnotationVisitor(
                pluginDescriptor = toDescriptor(pluginAnnotationClass),
                bootstrapDescriptors =
                    setOf(
                        toDescriptor(bootstrapAnnotationClass),
                        toDescriptor(bootstrapAnnotationAliasClass),
                    ),
            )

        reader.accept(
            visitor,
            ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES,
        )

        return visitor.toScannedClassInfo()
    }

    private fun toResult(
        scanned: List<ScannedClassInfo>,
        packageName: String,
    ): ScanResult {
        val pluginClasses = scanned.filter { it.hasPlugin }
        val bootstrapClass = scanned.firstOrNull { it.hasBootstrap }

        return ScanResult(
            pluginClass = pluginClasses.firstOrNull(),
            bootstrapClass = bootstrapClass,
            allPluginClasses = pluginClasses,
            packageName = packageName,
        )
    }

    private fun isInsideTargetPackage(
        classPackage: String,
        targetPackage: String,
        includeSubpackages: Boolean,
    ): Boolean =
        if (includeSubpackages) {
            classPackage == targetPackage || classPackage.startsWith("$targetPackage.")
        } else {
            classPackage == targetPackage
        }

    private fun toDescriptor(fqcn: String): String = "L${fqcn.replace('.', '/')};"
}
