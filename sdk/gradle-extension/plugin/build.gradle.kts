plugins {
    `java-gradle-plugin`
    id("org.gradle.kotlin.kotlin-dsl") version "6.7.3"
}

dependencies {
    implementation("org.ow2.asm:asm:9.11.1")

    implementation(gradleApi())
    testImplementation(gradleTestKit())
    testImplementation("org.junit.jupiter:junit-jupiter:6.1.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.1.3")
}

tasks.test {
    useJUnitPlatform()
}

gradlePlugin {
    plugins {
        create("sectilePlugin") {
            id = "dev.sectile.plugin-metadata"
            implementationClass = "dev.sectile.gradle.SectileGradlePlugin"
            displayName = "Sectile Plugin Metadata"
            description = "Generates plugin.properties from @Plugin annotation"
        }
    }
}
