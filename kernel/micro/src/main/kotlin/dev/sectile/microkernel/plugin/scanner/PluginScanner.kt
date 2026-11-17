package dev.sectile.microkernel.plugin.scanner

import dev.sectile.kernel.logger.Logger
import dev.sectile.microkernel.plugin.manifest.PluginManifestReader
import java.io.File
import java.util.jar.JarFile

internal class PluginScanner(
    private val manifestReader: PluginManifestReader,
    private val logger: Logger,
) {
    fun scan(directory: File): List<PluginCandidate> {
        if (!directory.exists() || !directory.isDirectory) {
            return emptyList()
        }
        val listFiles = directory.listFiles() ?: return emptyList()

        val candidates = ArrayList<PluginCandidate>()

        for (file in listFiles) {
            if (!file.isFile || file.extension != "jar") {
                continue
            }

            try {
                JarFile(file).use { jar ->
                    candidates.add(PluginCandidate(file, manifestReader.read(jar)))
                }
            } catch (e: Exception) {
                logger.error("Error scanning the file ${file.name}", e)
            }
        }

        return candidates
    }
}
