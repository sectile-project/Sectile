plugins {
    id("sectile.module")
}

dependencies {
    compileOnly(project(":networking:api"))
    compileOnly(project(":kernel:api"))

    testImplementation(project(":networking:api"))
}
