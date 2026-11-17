package dev.sectile.kernel.plugin.model.version

/**
 * Represents a semantic plugin version with an optional release channel.
 *
 * Examples: 1.0.0, 1.0.0-ALPHA, 1.2.3-BETA, 2.0.0-RC, 0.4.0-PRE_RELEASE
 */
data class Version(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val releaseChannel: ReleaseChannel = ReleaseChannel.STABLE,
) : Comparable<Version> {
    init {
        require(major >= 0) { "major must be greater than or equal to 0" }
        require(minor >= 0) { "minor must be greater than or equal to 0" }
        require(patch >= 0) { "patch must be greater than or equal to 0" }
    }

    override fun compareTo(other: Version): Int {
        val coreComparison =
            compareValuesBy(
                this,
                other,
                Version::major,
                Version::minor,
                Version::patch,
            )

        if (coreComparison != 0) {
            return coreComparison
        }

        return releaseChannel.compareStability(other.releaseChannel)
    }

    override fun toString(): String {
        val base = "$major.$minor.$patch"
        return if (releaseChannel == ReleaseChannel.STABLE) base else "$base-${releaseChannel.name}"
    }

    companion object {
        fun parse(raw: String): Version {
            val input = raw.trim()

            val dash = input.indexOf('-')
            val core = if (dash == -1) input else input.substring(0, dash)

            val firstDot = core.indexOf('.')
            val secondDot = core.indexOf('.', firstDot + 1)

            require(firstDot != -1 && secondDot != -1) { "Invalid version: $raw" }

            val channelRaw = if (dash == -1) null else input.substring(dash + 1)
            val releaseChannel = if (channelRaw == null) ReleaseChannel.STABLE else ReleaseChannel.parse(channelRaw)

            val major = parseNumber(core, 0, firstDot, raw)
            val minor = parseNumber(core, firstDot + 1, secondDot, raw)
            val patch = parseNumber(core, secondDot + 1, core.length, raw)

            return Version(major, minor, patch, releaseChannel)
        }

        private fun parseNumber(s: String, start: Int, end: Int, raw: String): Int {
            require(end > start) { "Invalid version: $raw" }

            var result = 0L
            for (i in start until end) {
                val c = s[i]
                require(c in '0'..'9') { "Invalid version: $raw" }
                result = result * 10 + (c - '0')
                require(result <= Int.MAX_VALUE) { "Invalid version: $raw" }
            }
            return result.toInt()
        }
    }
}
