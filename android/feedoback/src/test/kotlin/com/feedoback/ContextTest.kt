package com.feedoback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The bounds mirror `customContextSchema` on the server. A value the SDK lets
 * through and the server refuses costs the visitor the whole thread, so these
 * are the numbers from lib/widget/visitor.ts and not round ones.
 */
class ContextTest {
    @Test
    fun `keeps what the server would take`() {
        val bounded = CustomContext.bounded(
            mapOf("plan" to "pro", "seats" to 12, "trial" to false, "invitedBy" to null),
        )

        assertEquals(4, bounded.size)
        assertEquals("pro", bounded["plan"])
        assertEquals(12, bounded["seats"])
        assertEquals(false, bounded["trial"])
        assertNull(bounded["invitedBy"])
        assertTrue("a null value is a key that was sent", bounded.containsKey("invitedBy"))
    }

    @Test
    fun `drops a key longer than the server allows`() {
        val long = "k".repeat(CustomContext.MAX_KEY_LENGTH + 1)
        val bounded = CustomContext.bounded(mapOf(long to "x", "plan" to "pro"))

        assertFalse(bounded.containsKey(long))
        assertEquals("pro", bounded["plan"])
    }

    @Test
    fun `drops an empty key`() {
        assertTrue(CustomContext.bounded(mapOf("" to "x")).isEmpty())
    }

    @Test
    fun `trims a long value rather than dropping it`() {
        val long = "a".repeat(CustomContext.MAX_VALUE_LENGTH + 50)
        assertEquals("a".repeat(500), CustomContext.bounded(mapOf("note" to long))["note"])
    }

    /** Half an emoji is not a character, and the cut is forced by the server's
     *  own measure, which counts the pair as two. */
    @Test
    fun `never cuts through a surrogate pair`() {
        val kept = CustomContext.bounded(mapOf("note" to "😀".repeat(300)))["note"] as String

        assertEquals(CustomContext.MAX_VALUE_LENGTH, kept.length)
        assertEquals(250, kept.codePointCount(0, kept.length))
        assertFalse(Character.isHighSurrogate(kept.last()))
    }

    @Test
    fun `drops a number that is not finite`() {
        val bounded = CustomContext.bounded(mapOf("ratio" to Double.POSITIVE_INFINITY, "seats" to 12))

        assertFalse(bounded.containsKey("ratio"))
        assertEquals(12, bounded["seats"])
    }

    /** A list or a map has no shape the server stores, and stringifying one
     *  would file a memory address under somebody's feedback. */
    @Test
    fun `drops a value with no shape the server stores`() {
        val bounded = CustomContext.bounded(
            mapOf("tags" to listOf("a", "b"), "nested" to mapOf("a" to 1), "plan" to "pro"),
        )

        assertEquals(mapOf("plan" to "pro"), bounded)
    }

    @Test
    fun `keeps the first thirty keys in a settled order`() {
        val context = (0 until 50).associate { String.format("k%02d", it) to it }

        val bounded = CustomContext.bounded(context)

        assertEquals(CustomContext.MAX_KEYS, bounded.size)
        assertEquals(0, bounded["k00"])
        assertEquals(29, bounded["k29"])
        assertFalse("and the same thirty on every launch", bounded.containsKey("k30"))
    }
}
