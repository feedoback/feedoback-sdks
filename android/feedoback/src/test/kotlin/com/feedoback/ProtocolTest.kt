package com.feedoback

import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The SDK against the fixtures in `packages/protocol/fixtures`.
 *
 * Those files are the only thing this end and the server both read. Decoding
 * every response and encoding a request that matches theirs is what stops the
 * two drifting — and it is why the iOS tests read the same files.
 */
class ProtocolTest {
    private fun fixture(name: String): JSONObject {
        // Found by walking up from the module, so the test needs no copy of
        // the fixtures that could go stale.
        val packages = File(System.getProperty("user.dir")!!).parentFile!!.parentFile!!
        return JSONObject(File(packages, "protocol/fixtures/$name").readText())
    }

    // MARK: what the server answers

    @Test
    fun `decodes an enabled config`() {
        val config = ConfigResponse.from(fixture("config.ios.json"))

        assertEquals("Acme Shop", config.projectName)
        assertTrue(config.enabled)
        assertNull(config.reason)
        assertEquals("#0f6e56", config.appearance.color)
        assertEquals("Feedback", config.appearance.label)
        assertTrue(config.appearance.rating)
    }

    /** An app is offered one way in; the server reduces the rest before answering. */
    @Test
    fun `an app is offered one way in`() {
        val actions = ConfigResponse.from(fixture("config.ios.json")).appearance.actions

        assertFalse(actions.point)
        assertFalse(actions.record)
        assertTrue(actions.feedback)
    }

    /** Light and dark is the device's call, so the server does not send it. */
    @Test
    fun `the server never tells an app which theme to be`() {
        assertFalse(fixture("config.ios.json").getJSONObject("appearance").has("theme"))
        assertTrue(fixture("config.web.json").getJSONObject("appearance").has("theme"))
    }

    @Test
    fun `decodes a dormant config with its reason`() {
        val config = ConfigResponse.from(fixture("config.ios.dormant.json"))

        assertFalse(config.enabled)
        assertEquals(Refusal.VISITOR_NOT_IDENTIFIED, config.reason)
        assertTrue(config.reason!!.advice.contains("identify()"))
    }

    /** Every refusal the server can send has to decode, or a project could not
     *  turn something on without an SDK update first. */
    @Test
    fun `every refusal decodes`() {
        val wire = listOf(
            "not-an-app", "mobile-not-enabled", "app-not-allowed",
            "visitor-not-identified", "visitor-not-verified", "visitor-not-listed",
        )
        wire.forEach { value ->
            val refusal = Refusal.from(value)
            assertNotNull("$value should decode", refusal)
            assertTrue(refusal!!.advice.isNotEmpty())
        }
    }

    // MARK: what the SDK sends

    @Test
    fun `decodes the thread fixture it is meant to produce`() {
        val request = ThreadRequest.from(fixture("thread.android.json"))

        assertEquals(FeedbackCategory.FEEDBACK, request.category)
        assertEquals(4, request.rating)
        assertEquals("checkout/payment", request.pageContext.route)
        assertEquals("com.acme.shop", request.pageContext.bundleId)
        assertEquals("Pixel 9", request.pageContext.deviceModel)
        assertEquals(Viewport(412, 915), request.pageContext.viewport)
        assertEquals(Orientation.PORTRAIT, request.pageContext.orientation)
        assertEquals(Network.CELLULAR, request.pageContext.network)
        assertEquals("u_112", request.visitor?.id)
    }

    /** The direction that matters: what this SDK writes has to be the object
     *  the server's schema accepts. */
    @Test
    fun `encodes back to the same object`() {
        val original = fixture("thread.android.json")
        val round = ThreadRequest.from(original).toJson()

        assertEquals(original.keys().asSequence().toSet(), round.keys().asSequence().toSet())
        assertEquals(original.toString().length > 0, true)
        original.keys().forEach { key ->
            assertEquals("key $key", original.get(key).toString(), round.get(key).toString())
        }
    }

    /** The iOS SDK writes this one; decoding it here is what keeps the two
     *  from describing the same screen in two different shapes. */
    @Test
    fun `the iOS fixture decodes too`() {
        val request = ThreadRequest.from(fixture("thread.ios.json"))

        assertEquals("ios", request.pageContext.platform)
        assertEquals(FeedbackCategory.BUG, request.category)
        assertEquals(64, request.visitor?.userHash?.length)
        assertEquals("image/jpeg", request.attachments?.first()?.contentType)
        assertEquals("pro", request.metadata?.get("plan"))
    }

    @Test
    fun `omits what was not set rather than sending null`() {
        val request = ThreadRequest(
            category = FeedbackCategory.FEEDBACK,
            body = "Short and plain",
            pageContext = ScreenContext(
                route = "home", title = "Home", bundleId = "com.acme.app",
                appVersion = "1.0", buildNumber = "1", osVersion = "15",
                deviceModel = "Pixel", locale = "en", timezone = "UTC",
                viewport = Viewport(412, 915), scale = 2.625,
                orientation = Orientation.PORTRAIT,
            ),
        ).toJson()

        assertFalse(request.has("rating"))
        assertFalse(request.has("visitor"))
        assertFalse(request.has("metadata"))
        assertFalse(request.has("attachments"))
        // And the context leaves out the network it could not read.
        assertFalse(request.getJSONObject("pageContext").has("network"))
        assertEquals("android", request.getJSONObject("pageContext").getString("platform"))
    }

    @Test
    fun `a visitor names somebody only with an id or an email`() {
        assertTrue(FeedbackVisitor(id = "u_1").isNamed)
        assertTrue(FeedbackVisitor(email = "ada@example.com").isNamed)
        assertFalse(FeedbackVisitor(name = "Ada").isNamed)
        assertFalse(FeedbackVisitor().isNamed)
        assertFalse(FeedbackVisitor(id = "").isNamed)
    }
}
