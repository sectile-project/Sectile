import org.gradle.api.Project
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.provider.Provider

fun Project.libsCatalog(): VersionCatalog =
    extensions.getByType(VersionCatalogsExtension::class.java).named("libs")

fun Project.library(
    name: String
): Provider<MinimalExternalModuleDependency> = libsCatalog().findLibrary(name).get()

fun Project.classified(
    alias: String,
    classifier: String,
): Provider<MinimalExternalModuleDependency> =
    dependencies.variantOf(libsCatalog().findLibrary(alias).get()) { classifier(classifier) }

fun Project.sectileProjects(property: String): List<Project> =
    providers.gradleProperty(property).orElse("").get()
        .split(",")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .mapNotNull { path ->
            rootProject.findProject(path).also {
                if (it == null) logger.lifecycle("[Sectile-build] skipping missing project: $path")
            }
        }
