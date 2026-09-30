package com.feedoback

/** What a visitor has written but not yet sent. */
data class Draft(
    val category: FeedbackCategory = FeedbackCategory.FEEDBACK,
    val body: String = "",
    /** One to five stars, when the project asks for them. */
    val rating: Int? = null,
    /** Typed into the sheet when the app never named anybody. */
    val email: String? = null,
    /** Encoded bytes of the screen, if the visitor kept the picture. */
    val screenshot: ByteArray? = null,
    val screenshotContentType: String = "image/jpeg",
) {
    /** A rating on its own is still feedback; an empty form is not. */
    val hasSomethingToSay: Boolean get() = body.isNotBlank() || rating != null

    // ByteArray breaks the generated equals, and a draft is compared in tests.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Draft) return false
        return category == other.category && body == other.body && rating == other.rating &&
            email == other.email && screenshot.contentEquals(other.screenshot) &&
            screenshotContentType == other.screenshotContentType
    }

    override fun hashCode(): Int =
        listOf(category, body, rating, email, screenshot?.size, screenshotContentType).hashCode()
}

sealed class SendOutcome {
    /** It reached the server. */
    data class Sent(val threadId: String) : SendOutcome()
    /** Nothing reached the server, so it is written down for the next launch. */
    object Queued : SendOutcome()
    /** The server said no, and will keep saying no. Never queued: retrying
     *  something that cannot succeed only wastes a device's battery. */
    data class RefusedBy(val reason: Refusal?) : SendOutcome()
    /** Everything else: a bad request, a server fault. Reported, not retried. */
    object Failed : SendOutcome()
}

/**
 * Everything between a visitor finishing a sentence and it arriving.
 *
 * No Android UI: it takes a draft and returns an outcome, which is what lets
 * the whole path be tested without a device.
 */
class Session(
    private val transport: Transport,
    private val store: Store,
    private val context: suspend () -> ScreenContext,
    private val metadata: suspend () -> Map<String, Any?> = { emptyMap() },
    private val log: Log = Log(LogLevel.WARNING),
) {
    /** Sends what the visitor wrote, or keeps it. */
    suspend fun send(draft: Draft): SendOutcome {
        if (!draft.hasSomethingToSay) return SendOutcome.Failed

        val screen = context()
        // Read when the visitor finishes writing, not when it is sent: a
        // thread queued on the checkout screen should not flush carrying the
        // context of wherever they happen to be three days later.
        val custom = metadata()
        var visitor = store.visitor()

        // An address typed into the sheet is the visitor's own word about
        // themselves, and the only way to answer someone the app never named.
        val typed = draft.email?.trim()
        if (!typed.isNullOrEmpty() && visitor?.email.isNullOrEmpty()) {
            visitor = FeedbackVisitor(visitor?.id, typed, visitor?.name, visitor?.userHash)
        }

        // The picture goes first, because the thread has to name it. Losing it
        // must not cost the words, so a failure here carries on without it.
        val attachments = mutableListOf<Attachment>()
        draft.screenshot?.let { bytes ->
            try {
                attachments.add(transport.upload(bytes, "screenshot", draft.screenshotContentType))
            } catch (error: Exception) {
                log.warning("the screenshot could not be uploaded; sending the message without it")
            }
        }

        val request = ThreadRequest(
            category = draft.category,
            body = draft.body.trim(),
            rating = draft.rating,
            pageContext = screen,
            visitor = visitor,
            metadata = custom.ifEmpty { null },
            attachments = attachments.ifEmpty { null },
            appVersion = screen.appVersion,
        )

        return post(request, queueIfUnreachable = true)
    }

    /** Tries everything written down earlier. Called on the next foreground. */
    suspend fun flushQueue(): Int {
        var sent = 0
        for (queued in store.queued()) {
            // The identity travelled with it; nothing here re-stamps it.
            when (post(queued.request, queueIfUnreachable = false)) {
                is SendOutcome.Sent -> {
                    store.remove(queued)
                    sent++
                }
                // It will not start working. Keeping it would mean trying
                // again on every launch, forever.
                is SendOutcome.RefusedBy, SendOutcome.Failed -> store.remove(queued)
                // Still no network. Stop: the rest will fare no better, and a
                // burst of doomed requests costs the device more than waiting.
                SendOutcome.Queued -> return sent
            }
        }
        return sent
    }

    private suspend fun post(request: ThreadRequest, queueIfUnreachable: Boolean): SendOutcome = try {
        SendOutcome.Sent(transport.createThread(request))
    } catch (error: TransportFailure.Unreachable) {
        keep(request, queueIfUnreachable) { log.debug("no network; kept this one for the next launch") }
    } catch (error: TransportFailure.RateLimited) {
        // Also worth keeping: it is a "later", not a "no".
        keep(request, queueIfUnreachable) { log.warning("sending too fast; try again shortly") }
    } catch (error: TransportFailure.Refused) {
        log.warning(error.reason?.advice ?: "this project would not take the feedback")
        SendOutcome.RefusedBy(error.reason)
    } catch (error: Exception) {
        log.error("the feedback could not be sent: $error")
        SendOutcome.Failed
    }

    private fun keep(request: ThreadRequest, allowed: Boolean, note: () -> Unit): SendOutcome {
        if (allowed) {
            store.enqueue(request)
            note()
        }
        return SendOutcome.Queued
    }
}

enum class LogLevel(val rank: Int) {
    SILENT(0), ERROR(1), WARNING(2), DEBUG(3)
}

/**
 * The console, quietly. This runs inside somebody else's product, so it says
 * nothing in a release build unless the host app asked it to.
 */
class Log(private val level: LogLevel, private val out: (String) -> Unit = ::println) {
    fun error(message: String) = write(LogLevel.ERROR, message)
    fun warning(message: String) = write(LogLevel.WARNING, message)
    fun debug(message: String) = write(LogLevel.DEBUG, message)

    private fun write(at: LogLevel, message: String) {
        if (level.rank >= at.rank) out("[Feedoback] $message")
    }
}
