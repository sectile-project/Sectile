package dev.sectile.kernel.plugin.spi.lifecycle

import dev.sectile.kernel.plugin.library.LibrariesRequest

interface PluginBoostrap {
    fun boot(libraries: LibrariesRequest)
}
