plugins {
    id("sectile.module")
    id("dev.sectile.plugin-metadata")
}

dependencies {
    compileOnly(project(":kernel:api"))
    compileOnly(project(":sdk:common"))
    compileOnly(project(":sdk:gradle-extension:shared"))
}
