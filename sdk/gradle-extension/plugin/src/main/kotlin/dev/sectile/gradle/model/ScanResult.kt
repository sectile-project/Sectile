package dev.sectile.gradle.model

data class ScanResult(
    val pluginClass: ScannedClassInfo?,
    val bootstrapClass: ScannedClassInfo?,
    val allPluginClasses: List<ScannedClassInfo>,

    /**
     * Package this result belongs to.
     *
     * When the package was discovered from the project it is the one that contained the first
     * `@Plugin` class found while walking the package tree.
     */
    val packageName: String? = null,
)
