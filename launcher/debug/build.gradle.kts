plugins {
    id("sectile.module")
    alias(libs.plugins.shadow)
}

dependencies {
    (sectileProjects("publicProjectPaths") + sectileProjects("internalProjectPaths"))
        .forEach { implementation(it) }
}
