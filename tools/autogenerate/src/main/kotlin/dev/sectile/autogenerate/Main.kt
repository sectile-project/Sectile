package dev.sectile.autogenerate

import java.nio.file.Files
import java.nio.file.Path

private const val DEFAULT_INPUT_RELATIVE = "tools/autogenerate/input/PacketRegistry.java"
private const val LOCAL_INPUT_RELATIVE = "input/PacketRegistry.java"
private const val DEFAULT_OUTPUT_RELATIVE = "networking/protocol/src/main/kotlin/dev/sectile/network/protocol/ProtocolPacketTable.kt"

fun main(args: Array<String>) {
    val inputPath = args.getOrNull(0)?.let(Path::of) ?: resolveDefaultInput()
    val outputPath = args.getOrNull(1)?.let(Path::of) ?: resolveDefaultOutput()

    require(Files.isRegularFile(inputPath)) { "Input file not found: ${inputPath.toAbsolutePath()}" }

    val generated = ProtocolPacketTableGenerator().generate(inputPath)
    outputPath.parent?.let(Files::createDirectories)
    Files.writeString(outputPath, generated)

    println("Generated ${outputPath.toAbsolutePath()} from ${inputPath.toAbsolutePath()}")
}

private fun resolveDefaultInput(): Path {
    val repoRootPath = Path.of(DEFAULT_INPUT_RELATIVE)
    if (Files.isRegularFile(repoRootPath)) return repoRootPath
    return Path.of(LOCAL_INPUT_RELATIVE)
}

private fun resolveDefaultOutput(): Path {
    val repoRootPath = Path.of(DEFAULT_OUTPUT_RELATIVE)
    if (Files.exists(repoRootPath.parent)) return repoRootPath
    return Path.of("../../$DEFAULT_OUTPUT_RELATIVE").normalize()
}
