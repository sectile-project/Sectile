plugins {
    id("sectile.module")
}

dependencies {
    compileOnly(project(":kernel:api"))
    compileOnly(project(":networking:api"))
    compileOnly(project(":sdk:gradle-extension:shared"))
}
