package dev.sectile.plugin.debug

import dev.sectile.kernel.logger.logger
import dev.sectile.kernel.plugin.library.LibrariesRequest
import dev.sectile.kernel.plugin.library.Repository
import dev.sectile.kernel.plugin.spi.lifecycle.PluginBoostrap
import dev.sectile.sdk.plugin.annotation.Bootstrap

@Bootstrap
class DebugPluginBootstrap : PluginBoostrap {

    override fun boot(libraries: LibrariesRequest) {
        this.logger().info("Loading libraries")
        libraries.apply {
            repository(Repository.MAVEN_CENTRAL)
            // Example of a custom repository:
            // repository(Repository("https://repo.bestrepoever.com/", "Best repo"))

            // Dependencies can be declared with this format:
            dependency("org.apache.commons:commons-lang3:3.20.0")
            /* Or this:
            dependency(
                group = "org.apache.commons",
                artifact = "commons-lang3",
                version = "3.20.0"
            )*/
        }
    }
}
