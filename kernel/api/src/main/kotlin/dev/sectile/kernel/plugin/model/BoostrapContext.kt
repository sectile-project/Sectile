package dev.sectile.kernel.plugin.model

import dev.sectile.kernel.plugin.library.LibrariesRequest

data class BoostrapContext(
    val librariesRequest: LibrariesRequest,
) {
    inline fun BoostrapContext.request(block: BoostrapContext.() -> Unit) {
        block()
    }
}
