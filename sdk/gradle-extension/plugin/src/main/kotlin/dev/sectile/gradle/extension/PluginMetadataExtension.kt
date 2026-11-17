package dev.sectile.gradle.extension

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import javax.inject.Inject

abstract class PluginMetadataExtension @Inject constructor(
    objects: ObjectFactory
) {
    /**
     * Package that contains the `@Plugin` class.
     *
     * Optional: when it is not set, the metadata task walks the packages of the project from
     * the shallowest to the deepest until it finds the annotation. Set it only to override
     * that discovery.
     */
    val packageName: Property<String> = objects.property(String::class.java)

    val includeSubpackages: Property<Boolean> =
        objects.property(Boolean::class.java).convention(false)

    val pluginAnnotationClass: Property<String> =
        objects.property(String::class.java)
            .convention("dev.sectile.sdk.plugin.annotation.Plugin")

    val bootstrapAnnotationClass: Property<String> =
        objects.property(String::class.java)
            .convention("dev.sectile.sdk.plugin.annotation.Bootstrap")

    val bootstrapAnnotationAliasClass: Property<String> =
        objects.property(String::class.java)
            .convention("dev.sectile.sdk.plugin.annotation.Boostrap")

    val outputFileName: Property<String> =
        objects.property(String::class.java)
            .convention("manifest.properties")

    val failOnMultiplePlugins: Property<Boolean> =
        objects.property(Boolean::class.java).convention(false)
}
