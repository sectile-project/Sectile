package dev.sectile.kernel.plugin.model

import dev.sectile.kernel.plugin.model.dependency.DependencyKind
import dev.sectile.kernel.plugin.model.dependency.PluginDependency
import dev.sectile.kernel.plugin.model.version.Version

data class PluginMetadata(
    val id: PluginId,
    val version: Version,
    val description: String? = null,
    val authors: Set<String> = emptySet(),
    val dependencies: Collection<PluginDependency> = emptySet(),
) {
    val requiredDependencies: Set<PluginDependency>
        get() = dependencies.filterTo(linkedSetOf()) { it.kind == DependencyKind.REQUIRED }

    val optionalDependencies: Set<PluginDependency>
        get() = dependencies.filterTo(linkedSetOf()) { it.kind == DependencyKind.OPTIONAL }
}
