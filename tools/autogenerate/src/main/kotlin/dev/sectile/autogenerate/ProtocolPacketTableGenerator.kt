package dev.sectile.autogenerate

import java.nio.file.Files
import java.nio.file.Path

/**
 * Generates `ProtocolPacketTable.kt` from `PacketRegistry.java`.
 */
class ProtocolPacketTableGenerator {

    private data class ParsedPacket(
        val id: Int,
        val name: String,
        val constantName: String,
        val directionHelper: String,
        val channelName: String,
        val maxSize: Int,
        val compressed: Boolean,
    )

    fun generate(inputFile: Path): String {
        val source = Files.readString(inputFile)
        val entries = parseEntries(source)
        require(entries.isNotEmpty()) { "No packet registrations found in $inputFile" }

        val maxNameLength = entries.maxOf { it.constantName.length }
        val builder = StringBuilder(entries.size * 128)

        builder.appendLine("package dev.sectile.network.protocol")
        builder.appendLine()
        builder.appendLine("import dev.sectile.network.api.NetworkChannel")
        builder.appendLine("import dev.sectile.network.api.packet.PacketDefinition")
        builder.appendLine("import dev.sectile.network.api.packet.PacketDirection")
        builder.appendLine("import dev.sectile.network.api.packet.PacketRegistry")
        builder.appendLine()
        builder.appendLine("/**")
        builder.appendLine(" * Protocol packet metadata table derived from Hytale client.")
        builder.appendLine(" *")
        builder.appendLine(" * Each constant exposes a public [PacketDefinition] with the packet id, name,")
        builder.appendLine(" * direction, QUIC channel, maximum payload size, and compression flag so outbound")
        builder.appendLine(" * packets and inbound packet handlers can reference definitions directly without")
        builder.appendLine(" * duplicating protocol metadata.")
        builder.appendLine(" */")
        builder.appendLine("@Suppress(\"ktlint:standard:no-multi-spaces\")")
        builder.appendLine("object ProtocolPacketTable {")
        builder.appendLine()

        for ((id, name, constantName, directionHelper, channelName, maxSize, compressed) in entries) {
            val args = buildList {
                add(id.toString())
                add("\"$name\"")
                add(maxSize.toString())
                if (compressed) add("compressed = true")
                if (channelName != "DEFAULT") add("channel = NetworkChannel.$channelName")
            }
            val paddedName = constantName.padEnd(maxNameLength)
            builder.appendLine("    val $paddedName = ${if (directionHelper == "both") "  both" else directionHelper}(${args.joinToString(", ")})")
        }

        builder.appendLine()
        appendHelper(builder, "server", "TO_SERVER")
        builder.appendLine()
        appendHelper(builder, "client", "TO_CLIENT")
        builder.appendLine()
        appendHelper(builder, "both", "BOTH")
        builder.appendLine("}")

        return builder.toString()
    }

    private fun appendHelper(builder: StringBuilder, helperName: String, directionEnum: String) {
        builder.appendLine("    private fun $helperName(")
        builder.appendLine("        id: Int,")
        builder.appendLine("        name: String,")
        builder.appendLine("        maxSize: Int,")
        builder.appendLine("        compressed: Boolean = false,")
        builder.appendLine("        channel: NetworkChannel = NetworkChannel.DEFAULT,")
        builder.appendLine("    ): PacketDefinition {")
        builder.appendLine("        val definition = PacketDefinition(id, name, PacketDirection.$directionEnum, channel, maxSize, compressed)")
        builder.appendLine("        PacketRegistry.register(definition)")
        builder.appendLine("        return definition")
        builder.appendLine("    }")
    }

    private fun parseEntries(source: String): List<ParsedPacket> = REGISTER_REGEX.findAll(source).map { match ->
        val direction = when (match.groupValues[1]) {
            "ToServer" -> "server"
            "ToClient" -> "client"
            "Both" -> "both"
            else -> error("Unknown PacketDirection: ${match.groupValues[1]}")
        }
        val channel = toUpperSnakeCase(match.groupValues[2])
        val id = match.groupValues[3].toInt()
        val name = match.groupValues[4]
        val maxSize = match.groupValues[5].toInt()
        val compressed = match.groupValues[6].toBoolean()

        ParsedPacket(
            id = id,
            name = name,
            constantName = toUpperSnakeCase(name),
            directionHelper = direction,
            channelName = channel,
            maxSize = maxSize,
            compressed = compressed,
        )
    }.toList()

    private fun toUpperSnakeCase(value: String): String = value
        .replace(LOWER_OR_DIGIT_BEFORE_UPPER, "$1_$2")
        .replace(LETTER_BEFORE_DIGIT, "$1_$2")
        .replace(ACRONYM_BOUNDARY, "$1_$2")
        .uppercase()

    companion object {
        private val REGISTER_REGEX = Regex(
            """register\(\s*PacketDirection\.(ToServer|ToClient|Both)\s*,\s*NetworkChannel\.(\w+)\s*,\s*(\d+)\s*,\s*"([^"]+)"\s*,\s*[^,]+,\s*\d+\s*,\s*(\d+)\s*,\s*(true|false)\s*,""",
        )
        private val LOWER_OR_DIGIT_BEFORE_UPPER = Regex("([a-z0-9])([A-Z])")
        private val LETTER_BEFORE_DIGIT = Regex("([a-zA-Z])([0-9])")
        private val ACRONYM_BOUNDARY = Regex("([A-Z]+)([A-Z][a-z])")
    }
}
