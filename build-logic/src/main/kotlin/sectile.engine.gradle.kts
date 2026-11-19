plugins {
    id("sectile.module")
    `java-library`
}

dependencies {
    val kernel = project(":kernel:api")
    val network = project(":networking:api")
    val protocol = project(":networking:protocol")

    add("compileOnlyApi", kernel)
    add("compileOnlyApi", network)
    add("compileOnlyApi", protocol)
}
