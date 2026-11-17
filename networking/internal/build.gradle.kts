plugins {
    id("sectile.module")
}

dependencies {
    compileOnly(project(":kernel:api"))

    testImplementation(project(":kernel:api"))
    testImplementation(project(":sdk:bundled"))

    implementation(project(":networking:api"))
    implementation(project(":networking:protocol"))

    implementation(classified("zstd-jni", "win_amd64"))
    implementation(classified("zstd-jni", "linux_amd64"))

    implementation(library("netty-handler"))
    implementation(classified("netty-codec-quic", "windows-x86_64"))
    implementation(classified("netty-codec-quic", "linux-x86_64"))
    implementation(classified("netty-transport-native-epoll", "linux-x86_64"))
}
