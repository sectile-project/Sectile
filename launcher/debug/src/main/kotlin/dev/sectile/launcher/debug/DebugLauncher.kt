package dev.sectile.launcher.debug

import dev.sectile.boostrap.startServer

fun main() {
    println("Debug Launcher Starting...")

    // No isolation in debug mode
    startServer(Thread.currentThread().contextClassLoader, System.currentTimeMillis())
}
