plugins {
    base
    alias(libs.plugins.spotless) apply false
}

group = providers.gradleProperty("group").get()
version = providers.gradleProperty("version").get()