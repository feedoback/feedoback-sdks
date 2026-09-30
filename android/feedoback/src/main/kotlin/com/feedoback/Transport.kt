package com.feedoback

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Every call the SDK makes to the app, and nothing about how it looks.
 *
 * A native client sends no Origin — there is nothing for a browser to set —
 * so it declares itself instead, and the project's owner decides which apps
 * are allowed. The headers here are what that gate reads.
 *
 * `HttpURLConnection` rather than OkHttp: it is in the platform, and a
 * dependency added here is one every customer's build has to resolve.
 */
object Header {
    /** Which gate the request goes through. */
    const val PLATFORM = "X-Feedoback-Platform"
    /** The package name, read from the OS. Declared, never verified. */
    const val APP = "X-Feedoback-App"
    /**
     * A random id stored on first launch. A rate-limit bucket only: one device
     * in a loop is stopped without taking a whole carrier's worth of visitors
     * with it, because one address is thousands of people.
     */
    const val INSTALL = "X-Feedoback-Install"
    /** Which SDK and version. Diagnostic. */
    const val SDK = "X-Feedoback-SDK"
}

sealed class TransportFailure : Exception() {
    /** The project does not exist, or the key is wrong. */
    object UnknownProject : TransportFailure()
    /** The gate turned this client away. Never shown to a visitor. */
    data class Refused(val reason: Refusal?) : TransportFailure()
    data class RateLimited(val retryAfterSeconds: Long?) : TransportFailure()
    data class Server(val status: Int) : TransportFailure()
    /** No network, a timeout, a reset. Worth keeping for later. */
    object Unreachable : TransportFailure()
    object Malformed : TransportFailure()
}

/**
 * What the transport needs to know about the device it runs on. Passed in
 * rather than read, which is what makes it testable off a phone.
 */
data class ClientIdentity(
    val bundleId: String,
    val installId: String,
    val sdkVersion: String,
)

internal data class UploadTicket(val uploadUrl: String, val storageKey: String)

class Transport(
    private val host: String,
    private val projectKey: String,
    private val identity: ClientIdentity,
    private val open: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection },
) {
    private val base = host.trimEnd('/')

    private fun url(path: String, query: String = ""): URL =
        URL("$base/api/widget/${encode(projectKey)}/$path$query")

    private fun encode(value: String) = URLEncoder.encode(value, "UTF-8")

    private fun connect(url: URL, method: String): HttpURLConnection = open(url).apply {
        requestMethod = method
        connectTimeout = 15_000
        readTimeout = 20_000
        setRequestProperty(Header.PLATFORM, "android")
        setRequestProperty(Header.APP, identity.bundleId)
        setRequestProperty(Header.INSTALL, identity.installId)
        setRequestProperty(Header.SDK, "android/${identity.sdkVersion}")
    }

    /**
     * Whether this client may run the widget, and how the owner wants it to
     * look. A refusal comes back as a dormant config, not an error.
     */
    suspend fun config(visitor: FeedbackVisitor?): ConfigResponse = withContext(Dispatchers.IO) {
        val query = buildList {
            visitor?.id?.takeIf { it.isNotEmpty() }?.let { add("id=${encode(it)}") }
            visitor?.email?.takeIf { it.isNotEmpty() }?.let { add("email=${encode(it)}") }
            visitor?.name?.takeIf { it.isNotEmpty() }?.let { add("name=${encode(it)}") }
            visitor?.userHash?.takeIf { it.isNotEmpty() }?.let { add("hash=${encode(it)}") }
        }.joinToString("&").let { if (it.isEmpty()) "" else "?$it" }

        val body = send(connect(url("config", query), "GET"))
        try {
            ConfigResponse.from(JSONObject(body))
        } catch (error: Exception) {
            throw TransportFailure.Malformed
        }
    }

    /** Opens a thread. The id it returns is only useful for a log. */
    suspend fun createThread(request: ThreadRequest): String = withContext(Dispatchers.IO) {
        val connection = connect(url("threads"), "POST").apply {
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
        }
        val body = send(connection, request.toJson().toString().toByteArray())
        try {
            JSONObject(body).getString("threadId")
        } catch (error: Exception) {
            throw TransportFailure.Malformed
        }
    }

    /**
     * Bytes go through the app's own route, never straight to storage: that is
     * where the ticket, the size cap and the byte-metered limit are, and it
     * keeps bucket credentials out of anybody's app.
     */
    suspend fun upload(bytes: ByteArray, kind: String, contentType: String): Attachment =
        withContext(Dispatchers.IO) {
            val ask = connect(url("uploads"), "POST").apply {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
            val ticketBody = send(
                ask,
                JSONObject()
                    .put("kind", kind)
                    .put("contentType", contentType)
                    .put("sizeBytes", bytes.size)
                    .toString()
                    .toByteArray(),
            )

            val ticket = try {
                val json = JSONObject(ticketBody)
                UploadTicket(json.getString("uploadUrl"), json.getString("storageKey"))
            } catch (error: Exception) {
                throw TransportFailure.Malformed
            }

            // The ticket's URL is a path on the app the SDK was pointed at.
            val target = URL(URL(base), ticket.uploadUrl)
            val put = open(target).apply {
                requestMethod = "PUT"
                doOutput = true
                connectTimeout = 15_000
                readTimeout = 60_000
                setRequestProperty("Content-Type", contentType)
            }
            send(put, bytes)

            Attachment(kind, ticket.storageKey, contentType, bytes.size)
        }

    private fun send(connection: HttpURLConnection, body: ByteArray? = null): String {
        try {
            body?.let { connection.outputStream.use { stream -> stream.write(it) } }

            val status = connection.responseCode
            val text = when {
                status in 200..299 -> connection.inputStream.bufferedReader().use { it.readText() }
                else -> connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            }

            when {
                status in 200..299 -> return text
                status == 404 -> throw TransportFailure.UnknownProject
                status == 403 -> throw TransportFailure.Refused(reasonIn(text))
                status == 429 -> throw TransportFailure.RateLimited(
                    connection.getHeaderField("Retry-After")?.toLongOrNull()
                )
                else -> throw TransportFailure.Server(status)
            }
        } catch (failure: TransportFailure) {
            throw failure
        } catch (error: IOException) {
            // A timeout, a dropped connection, a tunnel: worth keeping for
            // later rather than telling the visitor their words are gone.
            throw TransportFailure.Unreachable
        } finally {
            connection.disconnect()
        }
    }

    private fun reasonIn(body: String): Refusal? = try {
        Refusal.from(JSONObject(body).optStringOrNull("reason"))
    } catch (error: Exception) {
        null
    }
}
