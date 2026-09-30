package com.feedoback.feedoback_flutter

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNull

/**
 * Turning what Dart measured into what the screenshot pass draws in.
 *
 * Flutter reports in its own logical pixels, relative to its own view; the SDK
 * paints in the root view's pixels. Getting the conversion wrong paints over
 * the wrong part of somebody's screen, and nothing about that is visible until
 * a screenshot arrives — so the rule is arithmetic that can be read and tested
 * without an emulator.
 */
class FeedobackFlutterPluginTest {
    private fun region(left: Double, top: Double, width: Double, height: Double) =
        mapOf<String, Any?>("left" to left, "top" to top, "width" to width, "height" to height)

    @Test
    fun `scales by the density`() {
        val bounds = FeedobackFlutterPlugin.pixelBounds(region(10.0, 20.0, 100.0, 40.0), 3f, 0, 0)
        assertContentEquals(intArrayOf(30, 60, 330, 180), bounds)
    }

    @Test
    fun `leaves a rectangle alone at one device pixel per logical pixel`() {
        val bounds = FeedobackFlutterPlugin.pixelBounds(region(10.0, 20.0, 100.0, 40.0), 1f, 0, 0)
        assertContentEquals(intArrayOf(10, 20, 110, 60), bounds)
    }

    /** Flutter's view does not always start at the top of the window — an
     *  embedded one starts wherever the host put it. */
    @Test
    fun `puts back where Flutter's own view starts`() {
        val bounds = FeedobackFlutterPlugin.pixelBounds(region(10.0, 20.0, 100.0, 40.0), 2f, 5, 63)
        assertContentEquals(intArrayOf(25, 103, 225, 183), bounds)
    }

    /** A widget that has not been laid out reports nothing worth painting. */
    @Test
    fun `drops a rectangle with nothing in it`() {
        assertNull(FeedobackFlutterPlugin.pixelBounds(region(10.0, 20.0, 0.0, 40.0), 2f, 0, 0))
        assertNull(FeedobackFlutterPlugin.pixelBounds(region(10.0, 20.0, 100.0, 0.0), 2f, 0, 0))
    }

    @Test
    fun `drops a rectangle that is missing a side`() {
        assertNull(
            FeedobackFlutterPlugin.pixelBounds(
                mapOf("left" to 10.0, "top" to 20.0, "width" to 100.0),
                2f,
                0,
                0,
            ),
        )
    }

    /** A fractional pixel is rounded, not truncated: a block half a pixel
     *  short of the text it covers is a block that does not cover it. */
    @Test
    fun `rounds rather than truncating`() {
        val bounds = FeedobackFlutterPlugin.pixelBounds(region(10.4, 20.6, 99.5, 40.4), 1f, 0, 0)
        assertContentEquals(intArrayOf(10, 21, 110, 61), bounds)
    }
}
