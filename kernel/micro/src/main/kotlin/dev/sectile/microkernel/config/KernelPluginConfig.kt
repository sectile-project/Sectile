package dev.sectile.microkernel.config

import dev.sectile.kernel.plugin.model.PluginId
import dev.sectile.kernel.plugin.model.version.Version
import java.nio.file.Path
import kotlin.time.Duration

class KernelPluginConfig(
    val paths: Paths,
    val selection: Selection,
    val loading: Loading,
    val logging: Logging,
) {
    class Paths(
        val plugins: Path,
        val libraries: Path,
    )

    class Selection(
        val mode: Mode,
        val plugins: Collection<PluginFormat>,
    ) {
        enum class Mode {
            ALL,
            BLACKLIST,
            WHITELIST,
        }

        data class PluginFormat(
            val id: PluginId,
            val version: Version?,
        )
    }

    class Loading(
        val parallel: Boolean,
        val timeOut: Duration,
    )

    class Logging(
        val lifecycleEvents: Boolean,
        val startupBuffer: Boolean,
        val bufferInitialCapacity: Int,
        val bufferMaxLines: Int,
        val overFlowStrategy: Strategy,
    ) {
        enum class Strategy {
            FLUSH,
            DROP_OLDEST,
            DROP_NEWEST,
        }
    }
}
