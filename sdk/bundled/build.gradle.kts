plugins {
    id("sectile.module")
}

dependencies {
    compileOnly(project(":kernel:api"))
    implementation(project(":sdk:common"))

    testImplementation(project(":kernel:api"))
}
