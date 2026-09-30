package com.feedoback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The map a bridge hands over, turned into a configuration.
 *
 * React Native and Flutter both arrive with one of these, so this is the one
 * place either can get it wrong. Nothing here invents a default: an absent or
 * unrecognised key leaves the SDK's own in place, which is the one a plain
 * Kotlin app gets.
 */
class ConfigurationTest {
    @Test
    fun `takes a project key and leaves everything else alone`() {
        val configuration = FeedobackConfiguration.from(mapOf("projectKey" to "pk_1"))!!
        val defaults = FeedobackConfiguration("pk_1")

        assertEquals("pk_1", configuration.projectKey)
        assertEquals(defaults.host, configuration.host)
        assertEquals(defaults.theme, configuration.theme)
        assertEquals(defaults.categories, configuration.categories)
        assertEquals(defaults.screenshots, configuration.screenshots)
        assertEquals(defaults.logLevel, configuration.logLevel)
        assertEquals(defaults.launcher, configuration.launcher)
    }

    @Test
    fun `carries every option a bridge can send`() {
        val configuration = FeedobackConfiguration.from(
            mapOf(
                "projectKey" to "pk_1",
                "host" to "https://feedback.acme.com",
                "theme" to "dark",
                "categories" to listOf("bug", "idea"),
                "screenshots" to "off",
                "logLevel" to "debug",
            ),
        )!!

        assertEquals("https://feedback.acme.com", configuration.host)
        assertEquals(Theme.DARK, configuration.theme)
        assertEquals(listOf(FeedbackCategory.BUG, FeedbackCategory.IDEA), configuration.categories)
        assertEquals(Screenshots.OFF, configuration.screenshots)
        assertEquals(LogLevel.DEBUG, configuration.logLevel)
    }

    /** The whole configuration hangs off it, so there is nothing to fall back
     *  to and nothing worth starting. */
    @Test
    fun `refuses to build without a project key`() {
        assertNull(FeedobackConfiguration.from(emptyMap()))
        assertNull(FeedobackConfiguration.from(mapOf("projectKey" to "   ")))
        assertNull(FeedobackConfiguration.from(mapOf("projectKey" to 42)))
    }

    @Test
    fun `falls back rather than guessing at a name it does not know`() {
        val configuration = FeedobackConfiguration.from(
            mapOf("projectKey" to "pk_1", "theme" to "midnight", "screenshots" to 3),
        )!!

        assertEquals(Theme.SYSTEM, configuration.theme)
        assertEquals(Screenshots.AUTOMATIC, configuration.screenshots)
    }

    @Test
    fun `keeps a category list free of repeats and of names it does not know`() {
        val configuration = FeedobackConfiguration.from(
            mapOf("projectKey" to "pk_1", "categories" to listOf("bug", "bug", "support", null)),
        )!!

        assertEquals(listOf(FeedbackCategory.BUG), configuration.categories)
    }

    /** A list with nothing usable in it is a list the app did not mean, so the
     *  SDK's own default survives rather than the sheet offering nothing. */
    @Test
    fun `falls back when a category list comes to nothing`() {
        val configuration = FeedobackConfiguration.from(
            mapOf("projectKey" to "pk_1", "categories" to listOf("support")),
        )!!

        assertEquals(listOf(FeedbackCategory.FEEDBACK), configuration.categories)
    }

    @Test
    fun `reads the launcher, corners and all`() {
        val configuration = FeedobackConfiguration.from(
            mapOf(
                "projectKey" to "pk_1",
                "launcher" to mapOf(
                    "enabled" to true,
                    "corner" to "bottom-start",
                    "style" to "labelled",
                    "draggable" to false,
                    "hidesWithKeyboard" to false,
                    "offset" to mapOf("x" to 20.0, "y" to 30.0),
                ),
            ),
        )!!

        assertEquals(
            LauncherOptions(
                enabled = true,
                corner = LauncherCorner.BOTTOM_START,
                offsetX = 20,
                offsetY = 30,
                style = LauncherStyle.LABELLED,
                draggable = false,
                hidesWithKeyboard = false,
            ),
            configuration.launcher,
        )
    }

    /** Start and end, not left and right: the launcher sits on the reading
     *  edge, and an Arabic app puts that on the other side. */
    @Test
    fun `reads the four corners a bridge names`() {
        fun corner(name: String) = FeedobackConfiguration.from(
            mapOf("projectKey" to "pk_1", "launcher" to mapOf("corner" to name)),
        )!!.launcher.corner

        assertEquals(LauncherCorner.TOP_START, corner("top-start"))
        assertEquals(LauncherCorner.TOP_END, corner("top-end"))
        assertEquals(LauncherCorner.BOTTOM_START, corner("bottom-start"))
        assertEquals(LauncherCorner.BOTTOM_END, corner("bottom-end"))
    }

    @Test
    fun `leaves the launcher off when the app did not ask for one`() {
        val configuration = FeedobackConfiguration.from(mapOf("projectKey" to "pk_1"))!!
        assertEquals(false, configuration.launcher.enabled)
    }
}
