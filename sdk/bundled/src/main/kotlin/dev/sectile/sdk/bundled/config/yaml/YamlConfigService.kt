package dev.sectile.sdk.bundled.config.yaml

import dev.sectile.kernel.config.ConfigSection
import dev.sectile.kernel.config.SaveOptions
import dev.sectile.sdk.bundled.config.AbstractConfigService
import java.nio.file.Files
import java.nio.file.Path

object YamlConfigService : AbstractConfigService() {
    override val fileExtensions = listOf("yml", "yaml")

    override fun load(path: Path): ConfigSection {
        val size = Files.size(path)
        if (size > Int.MAX_VALUE) {
            throw IllegalStateException("File size too large")
        }
        return load(Files.readAllBytes(path))
    }

    override fun load(byteArray: ByteArray, offset: Int, length: Int): ConfigSection = StrictYamlParser().parse(byteArray, offset, length)

    override fun save(
        path: Path,
        section: ConfigSection,
        options: SaveOptions,
    ) {
        Files.newBufferedWriter(path).use { writer ->
            writer.write(saveIntoString(section, options))
        }
    }

    override fun saveIntoString(section: ConfigSection, options: SaveOptions): String = SectionToYamlConverter(options.indentSpaces).toYaml(section)
}
