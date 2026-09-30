package com.feedoback

import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

/**
 * What survives a launch.
 *
 * Three things, and nothing else: a random install id, the identity the app
 * last declared, and threads that could not be sent yet. No advertising id,
 * no ANDROID_ID, no fingerprint — the only identifier minted here is a UUID
 * that dies with the app.
 */
interface Preferences {
    fun getString(key: String): String?
    fun putString(key: String, value: String?)
}

data class QueuedThread(val request: ThreadRequest, val queuedAtMillis: Long) {
    fun toJson(): JSONObject =
        JSONObject().put("request", request.toJson()).put("queuedAt", queuedAtMillis)

    companion object {
        fun from(json: JSONObject): QueuedThread = QueuedThread(
            request = ThreadRequest.from(json.getJSONObject("request")),
            queuedAtMillis = json.optLong("queuedAt"),
        )
    }
}

class Store(
    private val preferences: Preferences,
    private val now: () -> Long = System::currentTimeMillis,
) {
    companion object {
        /** An unbounded queue on somebody else's device is a bug, so it is
         *  capped three ways: how many, how old, and how much. */
        const val MAX_QUEUED_THREADS = 20
        const val MAX_QUEUE_AGE_MILLIS = 7L * 24 * 60 * 60 * 1000
        const val MAX_QUEUE_BYTES = 20 * 1024 * 1024

        private const val KEY_INSTALL = "com.feedoback.installId"
        private const val KEY_VISITOR = "com.feedoback.visitor"
        private const val KEY_QUEUE = "com.feedoback.queue"
    }

    /**
     * Minted on first launch and kept. Not an identity and not a secret: a
     * rate-limit bucket, so one device in a loop can be told to stop without
     * stopping everyone behind the same carrier address.
     */
    @Synchronized
    fun installId(): String {
        preferences.getString(KEY_INSTALL)?.takeIf { it.isNotEmpty() }?.let { return it }
        val fresh = UUID.randomUUID().toString()
        preferences.putString(KEY_INSTALL, fresh)
        return fresh
    }

    @Synchronized
    fun visitor(): FeedbackVisitor? = try {
        preferences.getString(KEY_VISITOR)?.let { FeedbackVisitor.from(JSONObject(it)) }
    } catch (error: Exception) {
        null
    }

    @Synchronized
    fun setVisitor(visitor: FeedbackVisitor?) {
        if (visitor == null || !visitor.isNamed) {
            preferences.putString(KEY_VISITOR, null)
            return
        }
        preferences.putString(KEY_VISITOR, visitor.toJson().toString())
    }

    /** What an app calls on sign-out. The install id stays: it is the same
     *  device, and it identifies nobody. */
    @Synchronized
    fun reset() {
        preferences.putString(KEY_VISITOR, null)
    }

    @Synchronized
    fun queued(): List<QueuedThread> = try {
        val array = JSONArray(preferences.getString(KEY_QUEUE) ?: "[]")
        (0 until array.length())
            .map { QueuedThread.from(array.getJSONObject(it)) }
            .filter { now() - it.queuedAtMillis < MAX_QUEUE_AGE_MILLIS }
    } catch (error: Exception) {
        emptyList()
    }

    /**
     * Keeps what the visitor wrote when the network would not take it.
     *
     * The identity is stamped in by the caller before this is reached, and
     * never at flush time: feedback written by one signed-in person must not
     * go out attributed to whoever signed in after them.
     */
    @Synchronized
    fun enqueue(request: ThreadRequest) {
        val all = queued().toMutableList()
        all.add(QueuedThread(request, now()))

        // Oldest out first: the newest complaint is the one still worth having.
        while (all.size > MAX_QUEUED_THREADS) all.removeAt(0)
        while (all.size > 1 && write(all).length > MAX_QUEUE_BYTES) all.removeAt(0)

        preferences.putString(KEY_QUEUE, write(all))
    }

    @Synchronized
    fun remove(thread: QueuedThread) {
        preferences.putString(KEY_QUEUE, write(queued().filter { it != thread }))
    }

    @Synchronized
    fun clearQueue() {
        preferences.putString(KEY_QUEUE, null)
    }

    private fun write(threads: List<QueuedThread>): String =
        JSONArray().apply { threads.forEach { put(it.toJson()) } }.toString()
}
