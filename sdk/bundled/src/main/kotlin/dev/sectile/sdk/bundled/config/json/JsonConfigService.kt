package dev.sectile.sdk.bundled.config.json

import dev.sectile.kernel.config.ConfigSection
import dev.sectile.kernel.config.SaveOptions
import dev.sectile.sdk.config.AbstractConfigService
import java.nio.file.Files
import java.nio.file.Path

object JsonConfigService : AbstractConfigService() {
    override val fileExtensions: List<String> = listOf("json")

    override fun load(path: Path): ConfigSection = load(Files.readAllBytes(path))
    override fun load(byteArray: ByteArray, offset: Int, length: Int): ConfigSection = JsonParser().parseObject(byteArray, offset, length)

    override fun save(
        path: Path,
        section: ConfigSection,
        options: SaveOptions,
    ) {
        Files.newBufferedWriter(path).use { writer ->
            writer.write(saveIntoString(section, options))
        }
    }

    override fun saveIntoString(section: ConfigSection, options: SaveOptions): String = SectionToJsonConverter(options.indentSpaces).toJson(section)
}
