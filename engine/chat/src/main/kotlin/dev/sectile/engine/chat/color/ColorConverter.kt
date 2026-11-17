package dev.sectile.engine.chat.color

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object ColorConverter {
    fun cymkToRgb(cyan: Float, yellow: Float, magenta: Float, key: Float): IntArray {
        val r = (255 * (1 - cyan) * (1 - key)).roundToInt()
        val g = (255 * (1 - magenta) * (1 - key)).roundToInt()
        val b = (255 * (1 - yellow) * (1 - key)).roundToInt()
        return intArrayOf(r, g, b)
    }

    fun rgbToCYMK(red: Int, green: Int, blue: Int): DoubleArray {
        val rNorm = red / 255.0
        val gNorm = green / 255.0
        val bNorm = blue / 255.0

        val k = 1 - max(max(rNorm, gNorm), bNorm)

        var c = ((1 - rNorm - k) / (1 - k)).toFloat()
        var m = ((1 - gNorm - k) / (1 - k)).toFloat()
        var y = ((1 - bNorm - k) / (1 - k)).toFloat()
        if (k == 1.0) {
            c = 0f
            m = 0f
            y = 0f
        }
        return doubleArrayOf(c.toDouble(), m.toDouble(), y.toDouble(), k)
    }

    fun hexToRGB(hex: String): IntArray {
        var hex = hex
        if (hex[0] == '#') {
            hex = hex.substring(1)
        }
        val red: Int
        val green: Int
        val blue: Int
        if (hex.length == 3) {
            // RGB to RRGGBB
            red = (hex.substring(0, 1) + hex.substring(0, 1)).toInt(16)
            green = (hex.substring(1, 2) + hex.substring(1, 2)).toInt(16)
            blue = (hex.substring(2, 3) + hex.substring(2, 3)).toInt(16)
        } else if (hex.length == 6) {
            // RRGGBB
            red = hex.substring(0, 2).toInt(16)
            green = hex.substring(2, 4).toInt(16)
            blue = hex.substring(4, 6).toInt(16)
        } else {
            red = 255
            green = 255
            blue = 255
        }
        return intArrayOf(red, green, blue)
    }

    fun rgbToHsl(red: Int, green: Int, blue: Int): DoubleArray {
        val rNorm = (red / 255.0f).toDouble()
        val gNorm = (green / 255.0f).toDouble()
        val bNorm = (blue / 255.0f).toDouble()

        val cMax = max(rNorm, max(gNorm, bNorm))
        val cMin = min(rNorm, min(gNorm, bNorm))
        val delta = cMax - cMin

        var h = 0.0
        if (delta == 0.0) {
            h = 0.0
        } else if (cMax == rNorm) {
            h = (60 * ((gNorm - bNorm) / delta) + 360) % 360
        } else if (cMax == gNorm) {
            h = (60 * ((bNorm - rNorm) / delta) + 120) % 360
        } else if (cMax == bNorm) {
            h = (60 * ((rNorm - gNorm) / delta) + 240) % 360
        }

        val l = (cMax + cMin) / 2

        var s = 0.0
        if (delta != 0.0) {
            s = delta / (1 - abs(2 * l - 1))
        }
        return doubleArrayOf(h.roundToInt().toDouble(), s, l)
    }
}
