package dev.sectile.gradle.task

import dev.sectile.gradle.scanner.PluginClassScanner
import dev.sectile.gradle.validation.Validator
import dev.sectile.gradle.writer.PluginPropertiesWriter
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

@CacheableTask
abstract class GeneratePluginMetadataTask : DefaultTask() {

    @get:Classpath
    abstract val classesDirs: ConfigurableFileCollection

    /**
     * Package to scan. Optional: when it is not set the plugin walks the packages of the
     * project, from the shallowest to the deepest, until it finds the `@Plugin` annotation.
     */
    @get:Input
    @get:Optional
    abstract val packageName: Property<String>

    @get:Input
    abstract val includeSubpackages: Property<Boolean>

    @get:Input
    abstract val pluginAnnotationClass: Property<String>

    @get:Input
    abstract val bootstrapAnnotationClass: Property<String>

    @get:Input
    abstract val bootstrapAnnotationAliasClass: Property<String>

    @get:Input
    abstract val outputFileName: Property<String>

    @get:Input
    abstract val failOnMultiplePlugins: Property<Boolean>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val scanner =
            PluginClassScanner(
                pluginAnnotationClass.get(),
                bootstrapAnnotationClass.get(),
                bootstrapAnnotationAliasClass.get(),
            )

        val configuredPackage = packageName.orNull?.trim()?.takeIf { it.isNotEmpty() }

        val scanResult =
            if (configuredPackage != null) {
                scanner.scan(classesDirs.files, configuredPackage, includeSubpackages.get())
            } else {
                scanner.discover(classesDirs.files)
            }

        if (scanResult.pluginClass == null) {
            logger.lifecycle("No @Plugin annotation found in the project classes. Nothing to generate.")
            cleanOutputDirectory()
            return
        }

        val scannedPackage = scanResult.packageName
        if (configuredPackage == null && scannedPackage != null) {
            logger.lifecycle("Discovered @Plugin in package '{}'.", scannedPackage)
        }

        if (failOnMultiplePlugins.get() && scanResult.allPluginClasses.size > 1) {
            throw GradleException(
                buildString {
                    appendLine("Multiple @Plugin annotations found in package '$scannedPackage'.")
                    appendLine("Matches:")
                    scanResult.allPluginClasses.forEach { appendLine(" - ${it.className}") }
                },
            )
        }

        val pluginData =
            scanResult.pluginClass.pluginData
                ?: throw GradleException("Internal error: @Plugin class was found but no annotation data was extracted")

        Validator.validatePluginData(pluginData, scanResult.pluginClass.className)

        val outputFile = outputDirectory.get().asFile.resolve(outputFileName.get())
        PluginPropertiesWriter().write(
            outputFile,
            pluginData,
            scanResult.pluginClass.className,
            scanResult.bootstrapClass?.className,
        )

        logger.lifecycle(
            "Generated {} from @Plugin={}{}",
            outputFile.absolutePath,
            scanResult.pluginClass.className,
            scanResult.bootstrapClass?.let { ", @Bootstrap=${it.className}" } ?: "",
        )
    }

    private fun cleanOutputDirectory() {
        val outputDir = outputDirectory.asFile.orNull ?: return
        if (outputDir.exists()) {
            outputDir.deleteRecursively()
        }
    }
}
