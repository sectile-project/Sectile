package dev.sectile.microkernel.plugin.manifest

import dev.sectile.kernel.plugin.exception.PluginDescriptorException
import dev.sectile.kernel.plugin.model.PluginId
import dev.sectile.kernel.plugin.model.PluginMetadata
import dev.sectile.kernel.plugin.model.dependency.DependencyKind
import dev.sectile.kernel.plugin.model.dependency.PluginDependency
import dev.sectile.kernel.plugin.model.version.Version
import java.util.jar.JarFile

/**
 * Reads plugin.properties from a plugin jar.
 *
 * Supported format:
 *  id=ExamplePlugin
 *  version=1.0.0
 *  main=com.example.ExamplePlugin
 *  description=Optional description
 *  authors=Alice,Bob
 *  dependencies=Kernel:1.0.0,Database:2.0.0
 *  softDependencies=Metrics:1.0.0
 */
private const val FILE_NAME = "manifest.properties"

class PluginPropertiesReader : PluginManifestReader {

    override fun read(jarFile: JarFile): PluginManifest {
        val entry = jarFile.getJarEntry(FILE_NAME)
            ?: throw PluginDescriptorException("Missing $FILE_NAME in plugin jar '${jarFile.name}'.")

        val properties = CustomPropertiesParser.parse(jarFile.getInputStream(entry))
        return readFromProperties(properties, jarFile.name)
    }

    private fun readFromProperties(properties: Map<String, String>, sourceName: String): PluginManifest {
        val id = required(properties, "id", sourceName)
        val version = required(properties, "version", sourceName)
        val mainClass = required(properties, "main", sourceName)
        val boostrapClass = properties["boostrap"]

        val dependencies = linkedSetOf<PluginDependency>()
        parseDependencies(
            properties["softDependencies"],
            DependencyKind.OPTIONAL,
            dependencies,
        )
        parseDependencies(
            properties["dependencies"],
            DependencyKind.REQUIRED,
            dependencies,
        )

        val metadata = PluginMetadata(
            PluginId(id),
            Version.parse(version),
            properties["description"]?.takeIf { it.isNotEmpty() },
            parseAuthors(properties["authors"]),
            dependencies,
        )

        return PluginManifest(metadata, mainClass, boostrapClass)
    }

    private fun required(properties: Map<String, String>, key: String, sourceName: String): String {
        var property = properties[key]
        if (property != null) {
            property = removeWhiteSpaces(property)
        }

        if (property.isNullOrEmpty()) {
            throw PluginDescriptorException(
                "Missing required property '$key' in '$sourceName'.",
            )
        }
        return property
    }

    private fun parseAuthors(raw: String?): Set<String> {
        if (raw.isNullOrBlank()) {
            return emptySet()
        }

        val authors = linkedSetOf<String>()
        for (author in raw.split(",")) {
            val author = author.trim()
            if (author.isNotEmpty()) {
                authors.add(author)
            }
        }

        return authors
    }

    private fun parseDependencies(line: String?, kind: DependencyKind, dependencies: MutableSet<PluginDependency>) {
        if (line.isNullOrBlank()) {
            return
        }
        for (dependency in line.split(",")) {
            val dependency = removeWhiteSpaces(dependency)
            if (dependency.isEmpty()) {
                continue
            }

            val parts = dependency.split(':')
                .map { removeWhiteSpaces(it) }
                .filter { it.isNotEmpty() }

            if (parts.size != 2) {
                throw PluginDescriptorException(
                    "Invalid dependency '$dependency' in '${kind.name}'. Expected PluginId:Version.",
                )
            }

            dependencies.add(
                PluginDependency(
                    PluginId(parts[0]),
                    Version.parse(parts[1]),
                    kind,
                ),
            )
        }
    }

    private fun removeWhiteSpaces(line: String): String = line.replace(" ", "")
}
