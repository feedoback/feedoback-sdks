package com.feedoback

import kotlin.math.pow

/**
 * The accent is the owner's, and can change without an app release.
 * Everything else is derived from it the way the web widget already derives
 * it, so the two cannot drift into different-looking products.
 *
 * Pure arithmetic on ARGB integers, with no `android.graphics.Color`: this is
 * maths, not platform, and keeping it that way is what lets a plain unit test
 * walk seven accents and hold each to AA without an emulator.
 */
object Palette {
    private const val FALLBACK = 0xFF0F6E56.toInt()

    private const val WHITE = 0xFFFFFFFF.toInt()
    private const val INK = 0xFF121212.toInt()

    fun accent(hex: String): Int = color(hex) ?: FALLBACK

    /**
     * White or ink, whichever can actually be read on the accent. Computed
     * rather than picked once, so a pale brand colour does not ship white text
     * on a pale button.
     */
    fun onAccent(accent: Int): Int = if (luminance(accent) > 0.5) INK else WHITE

    /** Six digits, with or without the hash. Anything else is not a colour. */
    fun color(hex: String): Int? {
        val value = hex.trim().removePrefix("#")
        if (value.length != 6) return null
        val number = value.toLongOrNull(16) ?: return null
        return (0xFF000000L or number).toInt()
    }

    fun red(color: Int): Int = (color shr 16) and 0xFF
    fun green(color: Int): Int = (color shr 8) and 0xFF
    fun blue(color: Int): Int = color and 0xFF

    /** Relative luminance, the same formula the contrast rules use. */
    fun luminance(color: Int): Double {
        fun channel(value: Int): Double {
            val scaled = value / 255.0
            return if (scaled <= 0.03928) scaled / 12.92 else ((scaled + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(red(color)) +
            0.7152 * channel(green(color)) +
            0.0722 * channel(blue(color))
    }

    fun contrast(a: Int, b: Int): Double {
        val first = luminance(a)
        val second = luminance(b)
        return (maxOf(first, second) + 0.05) / (minOf(first, second) + 0.05)
    }
}
