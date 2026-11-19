plugins {
    id("sectile.engine")
}

dependencies {
    compileOnlyApi(project(":engine:api"))

    testImplementation(project(":engine:api"))
    testImplementation(project(":networking:api"))
    testImplementation(project(":networking:protocol"))
}
