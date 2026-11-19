plugins {
    id("sectile.module")
    `java-library`
}

dependencies {
    compileOnlyApi(project(":kernel:api"))
    compileOnlyApi(project(":networking:api"))
    compileOnlyApi(project(":engine:api"))
    compileOnlyApi(project(":engine:chat"))
    compileOnlyApi(project(":sdk:gradle-extension:shared"))
}
