package dev.sectile.sdk.plugin.annotation

@Target()
@Retention(AnnotationRetention.BINARY)
annotation class Dependency(
    val id: String,
    val version: String = "",
)
