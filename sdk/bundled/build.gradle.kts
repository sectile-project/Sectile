plugins {
    id("sectile.module")
}

dependencies {
    compileOnly(project(":kernel:api"))
    api(project(":sdk:common"))

    testImplementation(project(":kernel:api"))
}
