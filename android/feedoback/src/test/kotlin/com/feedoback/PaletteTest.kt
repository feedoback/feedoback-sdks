package com.feedoback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The accent is the owner's and can be anything. What is derived from it has
 * to stay readable, which is not something to eyeball once and hope.
 *
 * Needs no emulator, because the palette is arithmetic rather than platform:
 * android.graphics.Color is stubbed in a local unit test, and a colour rule
 * that could only be checked on a device would not be checked at all.
 */
class PaletteTest {
    @Test
    fun `reads a hex colour`() {
        val colour = Palette.color("#0f6e56")
        assertNotNull(colour)
        assertEquals(0x0f, Palette.red(colour!!))
        assertEquals(0x6e, Palette.green(colour))
        assertEquals(0x56, Palette.blue(colour))
    }

    @Test
    fun `falls back rather than drawing nothing`() {
        assertNull(Palette.color("not a colour"))
        assertNull(Palette.color("#abc"))
        // An unreadable value still gives a launcher somebody can see.
        assertNotNull(Palette.accent("nonsense"))
    }

    /** Picked by contrast rather than chosen once, so a pale brand colour does
     *  not ship white text on a pale button. */
    @Test
    fun `text on the accent is always readable`() {
        val accents = listOf("#0f6e56", "#ffffff", "#000000", "#ffe600", "#1d4ed8", "#f5f5f5", "#7c3aed")

        accents.forEach { hex ->
            val accent = Palette.accent(hex)
            val ratio = Palette.contrast(accent, Palette.onAccent(accent))
            assertTrue("$hex needs AA for the word on the launcher, got $ratio", ratio >= 4.5)
        }
    }

    @Test
    fun `a pale accent takes ink and a dark one takes white`() {
        assertTrue(Palette.luminance(Palette.onAccent(Palette.accent("#ffe600"))) < 0.5)
        assertTrue(Palette.luminance(Palette.onAccent(Palette.accent("#0f6e56"))) > 0.5)
    }
}
