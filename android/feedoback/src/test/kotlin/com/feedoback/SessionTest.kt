package com.feedoback

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Stands in for SharedPreferences, so a test never writes anywhere real. */
class MemoryPreferences : Preferences {
    private val values = mutableMapOf<String, String>()
    override fun getString(key: String): String? = values[key]
    override fun putString(key: String, value: String?) {
        if (value == null) values.remove(key) else values[key] = value
    }
}

/** Answers a request without a network, and remembers what was sent. */
class StubConnection(
    url: URL,
    private val status: Int,
    private val body: String,
    private val throwOnConnect: Boolean = false,
) : HttpURLConnection(url) {
    val sent = ByteArrayOutputStream()

    override fun connect() {
        if (throwOnConnect) throw IOException("no network")
    }

    override fun disconnect() {}
    override fun usingProxy(): Boolean = false
    override fun getOutputStream() = sent
    override fun getResponseCode(): Int {
        if (throwOnConnect) throw IOException("no network")
        return status
    }
    override fun getInputStream() = ByteArrayInputStream(body.toByteArray())
    override fun getErrorStream() = ByteArrayInputStream(body.toByteArray())
}

class SessionTest {
    private lateinit var preferences: MemoryPreferences
    private val connections = mutableListOf<StubConnection>()
    private var answers: MutableList<Pair<Int, String>> = mutableListOf()
    private var offline = false

    @Before
    fun setUp() {
        preferences = MemoryPreferences()
        connections.clear()
        answers = mutableListOf()
        offline = false
    }

    private fun screen() = ScreenContext(
        route = "cart", title = "Cart", bundleId = "com.acme.shop",
        appVersion = "2.8.1", buildNumber = "4213", osVersion = "15",
        deviceModel = "Pixel 9", locale = "en-GB", timezone = "Europe/London",
        viewport = Viewport(412, 915), scale = 2.625, orientation = Orientation.PORTRAIT,
    )

    private fun session(
        store: Store = Store(preferences),
        metadata: () -> Map<String, Any?> = { emptyMap() },
    ): Pair<Session, Store> {
        val transport = Transport(
            host = "https://feedoback.test",
            projectKey = "pk_test",
            identity = ClientIdentity("com.acme.shop", "install-1", "0.1.0"),
        ) { url ->
            val (status, body) = if (answers.isEmpty()) 201 to """{"threadId":"t_1"}"""
            else answers.removeAt(0)
            StubConnection(url, status, body, offline).also { connections.add(it) }
        }
        return Session(
            transport = transport,
            store = store,
            context = { screen() },
            metadata = { metadata() },
            log = Log(LogLevel.SILENT),
        ) to store
    }

    private fun bodyOf(index: Int = 0) = JSONObject(connections[index].sent.toString())

    // MARK: sending

    @Test
    fun `sends what the visitor wrote`() = runTest {
        val (session, store) = session()

        val outcome = session.send(Draft(body = "The cart total is wrong"))

        assertEquals(SendOutcome.Sent("t_1"), outcome)
        assertTrue("nothing to keep once it arrived", store.queued().isEmpty())
    }

    @Test
    fun `refuses to send an empty form`() = runTest {
        val (session, _) = session()

        assertEquals(SendOutcome.Failed, session.send(Draft(body = "   ")))
        assertTrue("and does not bother the server", connections.isEmpty())
    }

    /** A rating with no words is still feedback. */
    @Test
    fun `a rating on its own is worth sending`() = runTest {
        val (session, _) = session()
        assertEquals(SendOutcome.Sent("t_1"), session.send(Draft(body = "", rating = 4)))
    }

    @Test
    fun `carries the identity the app declared`() = runTest {
        val (session, store) = session()
        store.setVisitor(FeedbackVisitor(id = "u_1", name = "Ada"))

        session.send(Draft(body = "Hello"))

        assertEquals("u_1", bodyOf().getJSONObject("visitor").getString("id"))
    }

    /** The only way to answer someone the app never named. */
    @Test
    fun `an address typed into the sheet becomes the visitors own`() = runTest {
        val (session, _) = session()

        session.send(Draft(body = "Hello", email = " ada@example.com "))

        assertEquals("ada@example.com", bodyOf().getJSONObject("visitor").getString("email"))
    }

    @Test
    fun `does not overwrite an address the app already gave`() = runTest {
        val (session, store) = session()
        store.setVisitor(FeedbackVisitor(id = "u_1", email = "real@example.com"))

        session.send(Draft(body = "Hi", email = "typed@example.com"))

        assertEquals("real@example.com", bodyOf().getJSONObject("visitor").getString("email"))
    }

    // MARK: what the app attached

    @Test
    fun `carries the context the app attached`() = runTest {
        val (session, _) = session(metadata = { mapOf("plan" to "pro", "seats" to 12) })

        session.send(Draft(body = "Hello"))

        val metadata = bodyOf().getJSONObject("metadata")
        assertEquals("pro", metadata.getString("plan"))
        assertEquals(12, metadata.getInt("seats"))
    }

    /** An app that attached nothing sends no key at all, rather than an empty
     *  object the dashboard would then render as a block with no rows. */
    @Test
    fun `sends no context key when the app attached nothing`() = runTest {
        val (session, _) = session()

        session.send(Draft(body = "Hello"))

        assertFalse(bodyOf().has("metadata"))
    }

    // MARK: when it cannot get through

    @Test
    fun `keeps what the network would not take`() = runTest {
        offline = true
        val (session, store) = session()

        assertEquals(SendOutcome.Queued, session.send(Draft(body = "Written in a tunnel")))
        assertEquals("Written in a tunnel", store.queued().first().request.body)
    }

    /** A refusal will keep being a refusal. Retrying it on every launch would
     *  only cost the device battery. */
    @Test
    fun `does not keep something the server will never take`() = runTest {
        answers.add(403 to """{"error":"Not allowed","reason":"visitor-not-identified"}""")
        val (session, store) = session()

        val outcome = session.send(Draft(body = "Hello"))

        assertEquals(SendOutcome.RefusedBy(Refusal.VISITOR_NOT_IDENTIFIED), outcome)
        assertTrue(store.queued().isEmpty())
    }

    /** The words matter more than the picture. */
    @Test
    fun `sends the message even when the screenshot will not upload`() = runTest {
        answers.add(500 to "{}")
        answers.add(201 to """{"threadId":"t_1"}""")
        val (session, _) = session()

        val outcome = session.send(Draft(body = "Look at this", screenshot = ByteArray(128)))

        assertEquals(SendOutcome.Sent("t_1"), outcome)
        assertTrue("no picture, but the message arrived", !bodyOf(1).has("attachments"))
    }

    // MARK: the queue, later

    @Test
    fun `sends what it kept once there is a network again`() = runTest {
        offline = true
        val (session, store) = session()
        session.send(Draft(body = "One"))
        session.send(Draft(body = "Two"))

        offline = false
        assertEquals(2, session.flushQueue())
        assertTrue(store.queued().isEmpty())
    }

    /** Still no network: stop rather than fire a burst of doomed requests. */
    @Test
    fun `stops flushing the moment it cannot get through`() = runTest {
        offline = true
        val (session, store) = session()
        session.send(Draft(body = "One"))
        session.send(Draft(body = "Two"))
        connections.clear()

        assertEquals(0, session.flushQueue())
        assertEquals("one attempt, not one per queued thread", 1, connections.size)
        assertEquals("and nothing was thrown away", 2, store.queued().size)
    }

    /** The identity travels with the thread, not with whoever is signed in now. */
    @Test
    fun `a queued thread arrives under who wrote it`() = runTest {
        offline = true
        val (session, store) = session()
        store.setVisitor(FeedbackVisitor(id = "u_first"))
        session.send(Draft(body = "Mine"))

        // Somebody else signs in before the network comes back.
        store.reset()
        store.setVisitor(FeedbackVisitor(id = "u_second"))
        connections.clear()
        offline = false
        session.flushQueue()

        assertEquals("u_first", bodyOf().getJSONObject("visitor").getString("id"))
    }

    /**
     * Stamped when the visitor finished writing, not when it finally went:
     * feedback written on the pro plan does not arrive marked free because
     * they downgraded while it sat in a tunnel.
     */
    @Test
    fun `a queued thread arrives with the context it was written under`() = runTest {
        var plan = "pro"
        offline = true
        val (session, _) = session(metadata = { mapOf("plan" to plan) })
        session.send(Draft(body = "Written on pro"))

        plan = "free"
        offline = false
        assertEquals(1, session.flushQueue())

        val metadata = bodyOf(connections.size - 1).getJSONObject("metadata")
        assertEquals("pro", metadata.getString("plan"))
    }

    // MARK: the store

    @Test
    fun `mints an install id once and keeps it`() {
        val store = Store(preferences)
        assertEquals(store.installId(), store.installId())
        assertEquals(36, store.installId().length)
    }

    @Test
    fun `sign out forgets the person but not the device`() {
        val store = Store(preferences)
        val install = store.installId()
        store.setVisitor(FeedbackVisitor(id = "u_1"))

        store.reset()

        assertNull(store.visitor())
        assertEquals("the same device, identifying nobody", install, store.installId())
    }

    @Test
    fun `keeps nothing for a visitor that names nobody`() {
        val store = Store(preferences)
        store.setVisitor(FeedbackVisitor(name = "Ada"))
        assertNull(store.visitor())
    }

    @Test
    fun `drops the oldest once past the cap`() {
        val store = Store(preferences)
        repeat(Store.MAX_QUEUED_THREADS + 5) { index ->
            store.enqueue(ThreadRequest(FeedbackCategory.FEEDBACK, "message $index", pageContext = screen()))
        }

        val queued = store.queued()
        assertEquals(Store.MAX_QUEUED_THREADS, queued.size)
        assertEquals("message 5", queued.first().request.body)
        assertEquals("message 24", queued.last().request.body)
    }

    /** A week-old complaint is not worth sending, and is worth not keeping. */
    @Test
    fun `forgets what went stale`() {
        var clock = 1_800_000_000_000L
        val store = Store(preferences) { clock }
        store.enqueue(ThreadRequest(FeedbackCategory.FEEDBACK, "Ancient", pageContext = screen()))

        clock += Store.MAX_QUEUE_AGE_MILLIS + 60_000
        assertTrue(store.queued().isEmpty())
    }

    @Test
    fun `survives rubbish in storage`() {
        preferences.putString("com.feedoback.queue", "not json")
        preferences.putString("com.feedoback.visitor", "not json")

        val store = Store(preferences)
        assertTrue(store.queued().isEmpty())
        assertNull(store.visitor())
    }

    // MARK: declaring itself

    /** A native client sends no Origin, so this is what the gate reads instead. */
    @Test
    fun `every request declares the app and the install`() = runTest {
        val (session, _) = session()
        session.send(Draft(body = "Hello"))

        val headers = connections.first()
        assertEquals("android", headers.getRequestProperty(Header.PLATFORM))
        assertEquals("com.acme.shop", headers.getRequestProperty(Header.APP))
        assertEquals("install-1", headers.getRequestProperty(Header.INSTALL))
        assertEquals("android/0.1.0", headers.getRequestProperty(Header.SDK))
    }
}
