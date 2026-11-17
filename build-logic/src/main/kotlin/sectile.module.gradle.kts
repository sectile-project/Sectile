import com.diffplug.gradle.spotless.SpotlessExtension
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test

plugins {
    java
    kotlin("jvm")
    id("com.diffplug.spotless")
}

val libs = the<VersionCatalogsExtension>().named("libs")
val javaVersion = libs.findVersion("java").get().requiredVersion.toInt()

val baseGroup = providers.gradleProperty("group").get()
val subGroup = project.path.removePrefix(":").substringBeforeLast(":", "").replace(":", ".")
group = if (subGroup.isEmpty()) baseGroup else "$baseGroup.$subGroup"
version = providers.gradleProperty("version").get()

extensions.configure<JavaPluginExtension> {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(javaVersion))
    }
    withSourcesJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-parameters", "-Xlint:deprecation", "-Xlint:unchecked"))
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        events("failed", "skipped")
    }
}

dependencies {
    add("testImplementation", libs.findLibrary("junit-jupiter").get())
    add("testImplementation", libs.findLibrary("kotlin-test-junit5").get())
    add("testRuntimeOnly", libs.findLibrary("junit-platform-launcher").get())
}

extensions.configure<SpotlessExtension> {
    java {
        target("src/**/*.java")
        googleJavaFormat()
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlin {
        target("src/**/*.kt")
        ktlint().editorConfigOverride(
            mapOf("ktlint_standard_no-wildcard-imports" to "disabled")
        )
        trimTrailingWhitespace()
        endWithNewline()
    }
}

tasks.named("check") {
    dependsOn("spotlessCheck")
}
