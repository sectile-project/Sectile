plugins {
    id("sectile.module")
}

dependencies {
    val kernel = project(":kernel:api")
    val network = project(":networking:api")
    val protocol = project(":networking:protocol")

    add("compileOnly", kernel)
    add("compileOnly", network)
    add("compileOnly", protocol)
}
