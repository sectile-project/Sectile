plugins {
    id("sectile.module")
}

dependencies {
    implementation(library("jline-terminal"))
    implementation(library("jline-reader"))

    implementation(project(":kernel:api"))
    implementation(project(":kernel:micro"))

    implementation(project(":sdk:bundled"))
    implementation(project(":sdk:common"))

    implementation(project(":networking:api"))
    implementation(project(":networking:protocol"))
    implementation(project(":networking:internal"))

    implementation(project(":engine:api"))
    implementation(project(":engine:chat"))
    implementation(project(":engine:internal"))
    implementation(project(":engine:world"))
    implementation(project(":engine:command"))
}
