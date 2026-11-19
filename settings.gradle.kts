pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
    includeBuild("build-logic")
    includeBuild("sdk/gradle-extension/plugin")
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}

rootProject.name = "Sectile"

include(
    ":kernel:api",
    ":kernel:micro",

    ":networking:api",
    ":networking:internal",
    ":networking:protocol",

    ":sdk:bundled",
    ":sdk:common",
    ":sdk:gradle-extension:shared",
    ":sdk:gradle-extension:plugin",

    ":launcher:boostrap",
    ":launcher:debug",
    ":launcher:isolated",

    ":tools:autogenerate",

    ":engine:chat",
    ":engine:api",
    ":engine:internal",
    ":engine:world",
    ":engine:command"
)

file("example-plugins").listFiles()?.forEach { pluginDir ->
    if (pluginDir.isDirectory) {
        include("example-plugins:${pluginDir.name}")
    }
}
