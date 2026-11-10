package dev.icode.mobile.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import dev.icode.mobile.domain.AccentColorRules
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin

/**
 * OKLCH, the perceptual colour space the theme's hues are chosen in: equal
 * lightness looks equally light whatever the hue, so one lightness per theme
 * gives every hue the same readability. Björn Ottosson's matrices (2020).
 */
internal object Oklch {
    /**
     * The sRGB colour at [lightness] and [hue] with as much [chroma] as sRGB can
     * show there, reduced in steps of 0.002 until it fits.
     */
    fun color(lightness: Double, chroma: Double, hue: Double): Color {
        var c = chroma
        var rgb = linearRgb(lightness, c, hue)
        while (c > 0 && rgb.any { it < -GAMUT_EPSILON || it > 1 + GAMUT_EPSILON }) {
            c -= CHROMA_STEP
            rgb = linearRgb(lightness, c, hue)
        }
        val (r, g, b) = rgb.map { (encode(it.coerceIn(0.0, 1.0)) * 255).toFloat().let(Math::round) }
        return Color(red = r, green = g, blue = b)
    }

    /** The hue, in degrees, of an sRGB colour given as `0xRRGGBB`. */
    fun hue(rgb: Int): Double = lch(rgb)[2]

    /**
     * [rgb] (`0xRRGGBB`) as it is, if it is at least [minimum] : 1 against each of
     * [behind]; otherwise its lightness moved — [darker] or lighter — in steps of
     * 0.01, hue and chroma kept, until it is.
     */
    fun readable(rgb: Int, behind: List<Color>, minimum: Double, darker: Boolean): Color {
        val reads = { color: Color -> behind.all { AccentColorRules.contrastRatio(color.toArgb(), it.toArgb()) >= minimum } }
        val original = Color(red = rgb shr 16 and 0xFF, green = rgb shr 8 and 0xFF, blue = rgb and 0xFF)
        if (reads(original)) return original
        val (lightness, chroma, hue) = lch(rgb)
        var l = lightness
        while (l in 0.0..1.0) {
            l += if (darker) -LIGHTNESS_STEP else LIGHTNESS_STEP
            val candidate = color(l, chroma, hue)
            if (reads(candidate)) return candidate
        }
        return if (darker) Color.Black else Color.White
    }

    /**
     * A drawing's [colors] (`0xRRGGBB` each) as they are, when the one that reads best is at least
     * [minimum] : 1 against each of [behind]; otherwise all moved together — [darker] or lighter, by one
     * same lightness step, each its hue and chroma kept — until it is. The drawing keeps its own
     * contrasts, and reads (docs/file-kinds-icons-plan.md D7).
     */
    fun readableTogether(colors: List<Int>, behind: List<Color>, minimum: Double, darker: Boolean): List<Color> {
        val reads = { color: Color -> behind.all { AccentColorRules.contrastRatio(color.toArgb(), it.toArgb()) >= minimum } }
        val originals = colors.map { rgb -> Color(red = rgb shr 16 and 0xFF, green = rgb shr 8 and 0xFF, blue = rgb and 0xFF) }
        if (colors.isEmpty() || originals.any(reads)) return originals
        val lch = colors.map(::lch)
        var step = LIGHTNESS_STEP
        while (step <= 1.0) {
            val moved = lch.map { (l, c, h) -> color((if (darker) l - step else l + step).coerceIn(0.0, 1.0), c, h) }
            if (moved.any(reads)) return moved
            step += LIGHTNESS_STEP
        }
        return originals.map { if (darker) Color.Black else Color.White }
    }

    /** Lightness, chroma and hue (degrees) of an sRGB colour given as `0xRRGGBB`. */
    private fun lch(rgb: Int): DoubleArray {
        val (r, g, b) = listOf(rgb shr 16, rgb shr 8, rgb).map { decode((it and 0xFF) / 255.0) }
        val l = cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
        val m = cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
        val s = cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)
        val lightness = 0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s
        val a = 1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s
        val bb = 0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s
        return doubleArrayOf(lightness, hypot(a, bb), (Math.toDegrees(atan2(bb, a)) + 360) % 360)
    }

    private fun linearRgb(lightness: Double, chroma: Double, hue: Double): List<Double> {
        val a = chroma * cos(Math.toRadians(hue))
        val b = chroma * sin(Math.toRadians(hue))
        val l = (lightness + 0.3963377774 * a + 0.2158037573 * b).pow(3)
        val m = (lightness - 0.1055613458 * a - 0.0638541728 * b).pow(3)
        val s = (lightness - 0.0894841775 * a - 1.2914855480 * b).pow(3)
        return listOf(
            4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s,
            -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s,
            -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s,
        )
    }

    private fun encode(x: Double) = if (x <= 0.0031308) 12.92 * x else 1.055 * x.pow(1 / 2.4) - 0.055

    private fun decode(x: Double) = if (x <= 0.04045) x / 12.92 else ((x + 0.055) / 1.055).pow(2.4)

    private const val CHROMA_STEP = 0.002
    private const val LIGHTNESS_STEP = 0.01
    private const val GAMUT_EPSILON = 1e-6
}
