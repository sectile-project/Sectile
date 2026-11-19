plugins {
    id("sectile.engine")
}

dependencies {
    implementation(project(":engine:api"))
    implementation(project(":engine:internal"))

    testImplementation(project(":kernel:api"))
    testImplementation(project(":networking:api"))
    testImplementation(project(":networking:protocol"))
    testImplementation(project(":engine:chat"))
    testImplementation(project(":engine:world"))
    testImplementation(project(":sdk:common"))
}
