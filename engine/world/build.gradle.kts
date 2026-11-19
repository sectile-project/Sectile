plugins {
    id("sectile.engine")
}

dependencies {
    implementation(project(":engine:api"))
    implementation(project(":engine:internal"))
    testImplementation(project(":networking:api"))
}
