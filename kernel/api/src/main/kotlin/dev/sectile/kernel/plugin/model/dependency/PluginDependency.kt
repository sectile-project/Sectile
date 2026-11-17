package dev.sectile.kernel.plugin.model.dependency

import dev.sectile.kernel.plugin.model.PluginId
import dev.sectile.kernel.plugin.model.version.Version

data class PluginDependency(
    val id: PluginId,
    val version: Version,
    val kind: DependencyKind = DependencyKind.REQUIRED,
)
