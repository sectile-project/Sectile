package dev.sectile.engine.chat.color

/**
 * The [Color] class represents a color in a chat system.
 * It provides methods to create colors from different formats (RGB, CMYK, HEX, etc.)
 * and also includes a set of predefined colors.
 */
@JvmInline
value class Color private constructor(
    val rgb: Int,
) {
    val red: Int get() = (rgb shr 16) and 0xFF
    val green: Int get() = (rgb shr 8) and 0xFF
    val blue: Int get() = rgb and 0xFF

    companion object {
        val BLACK = rgb(0, 0, 0)
        val DARK_BLUE = rgb(0, 0, 170)
        val DARK_GREEN = rgb(0, 170, 0)
        val DARK_AQUA = rgb(0, 170, 170)
        val DARK_RED = rgb(170, 0, 0)
        val DARK_PURPLE = rgb(170, 0, 170)
        val GOLD = rgb(255, 170, 0)
        val GRAY = rgb(170, 170, 170)
        val DARK_GRAY = rgb(85, 85, 85)
        val BLUE = rgb(85, 85, 255)
        val GREEN = rgb(85, 255, 85)
        val AQUA = rgb(85, 255, 255)
        val RED = rgb(255, 85, 85)
        val LIGHT_PURPLE = rgb(255, 85, 255)
        val YELLOW = rgb(255, 255, 85)
        val WHITE = rgb(255, 255, 255)

        /**
         * Creates a color from integer RGB values. (0 to 255)
         */
        fun rgb(red: Int, green: Int, blue: Int): Color = Color(((red and 0xFF) shl 16) or ((green and 0xFF) shl 8) or (blue and 0xFF))

        /**
         * Creates a color from floating-point RGB values (0.0 to 1.0).
         */
        fun rgb(red: Float, green: Float, blue: Float): Color = rgb(
            (255 * red.coerceIn(0f, 1f)).toInt(),
            (255 * green.coerceIn(0f, 1f)).toInt(),
            (255 * blue.coerceIn(0f, 1f)).toInt(),
        )

        /**
         * Creates a color from CMYK values (0.0 to 1.0).
         */
        fun cmyk(cyan: Float, magenta: Float, yellow: Float, key: Float): Color {
            val rgb = ColorConverter.cymkToRgb(cyan, yellow, magenta, key)
            return rgb(rgb[0], rgb[1], rgb[2])
        }

        /**
         * Creates a color from a hexadecimal integer value.
         */
        fun hex(hex: Int): Color = Color(hex and 0xFFFFFF)

        /**
         * Creates a color from a hexadecimal string (e.g., "#RRGGBB" or "RRGGBB").
         */
        fun hex(hex: String): Color {
            val rgb = ColorConverter.hexToRGB(hex)
            return rgb(rgb[0], rgb[1], rgb[2])
        }

        /**
         * Creates a color from a gray scale (0-255).
         */
        fun gray(grayType: Int): Color {
            val gray = grayType and 0xFF
            return Color((gray shl 16) or (gray shl 8) or gray)
        }

        fun red(redType: Int): Color = Color((redType and 0xFF) shl 16)

        fun green(greenType: Int): Color = Color((greenType and 0xFF) shl 8)

        fun blue(blueType: Int): Color = Color(blueType and 0xFF)
    }

    override fun toString(): String = toHex()

    fun toRgb(): String = "$red, $green, $blue"

    fun toCymk(): String {
        val cymk = ColorConverter.rgbToCYMK(red, green, blue)
        return "${cymk[0]}, ${cymk[1]}, ${cymk[2]}, ${cymk[3]}"
    }

    fun toGray(): String = ((red + green + blue) / 3).toString()

    fun toHSL(): String {
        val hsl = ColorConverter.rgbToHsl(red, green, blue)
        return "${hsl[0]}, ${hsl[1]}, ${hsl[2]}"
    }

    fun toHex(): String = String.format("#%06X", rgb)
}
