package com.feedoback

import org.json.JSONArray
import org.json.JSONObject

/**
 * The wire, and nothing else.
 *
 * Every type here matches a fixture in `packages/protocol/fixtures`, which is
 * the only thing this SDK and the server both read. A field added on one side
 * and not the other fails a test rather than a customer.
 *
 * Hand-written JSON rather than a serialisation library: `org.json` is in the
 * platform, and a dependency added here is one every customer's build has to
 * resolve and every customer's app has to carry.
 */

/** What a thread is. Support was retired when the widget stopped offering it. */
enum class FeedbackCategory(val wire: String) {
    FEEDBACK("feedback"),
    BUG("bug"),
    IDEA("idea");

    companion object {
        fun from(value: String?): FeedbackCategory? = entries.firstOrNull { it.wire == value }
    }
}

/**
 * Who the host app says this is.
 *
 * Declared, never authoritative — it is the customer's own code asserting a
 * user id. [userHash] is the exception: their server signs the id with the
 * project's key, so a thread can only be opened for an account they vouched
 * for.
 */
data class FeedbackVisitor(
    val id: String? = null,
    val email: String? = null,
    val name: String? = null,
    /** Hex HMAC-SHA256 of the id (or the email, when there is no id). */
    val userHash: String? = null,
) {
    /** Whether this names anybody at all. A name on its own does not. */
    val isNamed: Boolean get() = !id.isNullOrEmpty() || !email.isNullOrEmpty()

    fun toJson(): JSONObject = JSONObject().apply {
        id?.takeIf { it.isNotEmpty() }?.let { put("id", it) }
        email?.takeIf { it.isNotEmpty() }?.let { put("email", it) }
        name?.takeIf { it.isNotEmpty() }?.let { put("name", it) }
        userHash?.takeIf { it.isNotEmpty() }?.let { put("userHash", it) }
    }

    companion object {
        fun from(json: JSONObject?): FeedbackVisitor? = json?.let {
            FeedbackVisitor(
                id = it.optStringOrNull("id"),
                email = it.optStringOrNull("email"),
                name = it.optStringOrNull("name"),
                userHash = it.optStringOrNull("userHash"),
            )
        }
    }
}

enum class Orientation(val wire: String) {
    PORTRAIT("portrait"),
    LANDSCAPE("landscape");

    companion object {
        fun from(value: String?): Orientation = entries.firstOrNull { it.wire == value } ?: PORTRAIT
    }
}

/** Coarse on purpose: whether they were online, never the carrier. */
enum class Network(val wire: String) {
    WIFI("wifi"),
    CELLULAR("cellular"),
    NONE("none");

    companion object {
        fun from(value: String?): Network? = entries.firstOrNull { it.wire == value }
    }
}

data class Viewport(val width: Int, val height: Int) {
    fun toJson(): JSONObject = JSONObject().put("width", width).put("height", height)

    companion object {
        fun from(json: JSONObject): Viewport =
            Viewport(json.optInt("width"), json.optInt("height"))
    }
}

/** A screen of an app, as the server's app context schema takes it. */
data class ScreenContext(
    val platform: String = "android",
    /** Where the visitor was, as the host app named it: "checkout/payment". */
    val route: String,
    /** What to call it in the list. Falls back to the route. */
    val title: String,
    val bundleId: String,
    val appVersion: String,
    val buildNumber: String,
    val osVersion: String,
    val deviceModel: String,
    val locale: String,
    val timezone: String,
    val viewport: Viewport,
    val scale: Double,
    val orientation: Orientation,
    val network: Network? = null,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("platform", platform)
        put("route", route)
        put("title", title.ifEmpty { route })
        put("bundleId", bundleId)
        put("appVersion", appVersion)
        put("buildNumber", buildNumber)
        put("osVersion", osVersion)
        put("deviceModel", deviceModel)
        put("locale", locale)
        put("timezone", timezone)
        put("viewport", viewport.toJson())
        put("scale", scale)
        put("orientation", orientation.wire)
        network?.let { put("network", it.wire) }
    }

    companion object {
        fun from(json: JSONObject): ScreenContext = ScreenContext(
            platform = json.optString("platform", "android"),
            route = json.optString("route"),
            title = json.optString("title"),
            bundleId = json.optString("bundleId"),
            appVersion = json.optString("appVersion"),
            buildNumber = json.optString("buildNumber"),
            osVersion = json.optString("osVersion"),
            deviceModel = json.optString("deviceModel"),
            locale = json.optString("locale"),
            timezone = json.optString("timezone"),
            viewport = Viewport.from(json.getJSONObject("viewport")),
            scale = json.optDouble("scale", 1.0),
            orientation = Orientation.from(json.optStringOrNull("orientation")),
            network = Network.from(json.optStringOrNull("network")),
        )
    }
}

data class Attachment(
    val kind: String,
    val storageKey: String,
    val contentType: String,
    val sizeBytes: Int,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("kind", kind)
        .put("storageKey", storageKey)
        .put("contentType", contentType)
        .put("sizeBytes", sizeBytes)

    companion object {
        fun from(json: JSONObject): Attachment = Attachment(
            kind = json.optString("kind"),
            storageKey = json.optString("storageKey"),
            contentType = json.optString("contentType"),
            sizeBytes = json.optInt("sizeBytes"),
        )
    }
}

data class ThreadRequest(
    val category: FeedbackCategory,
    val body: String,
    /** One to five stars, when the project asks and the visitor answered. */
    val rating: Int? = null,
    val pageContext: ScreenContext,
    val visitor: FeedbackVisitor? = null,
    val metadata: Map<String, Any?>? = null,
    val attachments: List<Attachment>? = null,
    /** The customer's own release, so feedback can be read per build. */
    val appVersion: String? = null,
) {
    /** A nil field is left out rather than sent as null, which the schema
     *  treats as absent either way but which keeps the payload honest. */
    fun toJson(): JSONObject = JSONObject().apply {
        put("category", category.wire)
        put("body", body)
        rating?.let { put("rating", it) }
        put("pageContext", pageContext.toJson())
        visitor?.let { put("visitor", it.toJson()) }
        metadata?.takeIf { it.isNotEmpty() }?.let { context ->
            put("metadata", JSONObject().apply {
                context.forEach { (key, value) -> put(key, value ?: JSONObject.NULL) }
            })
        }
        attachments?.takeIf { it.isNotEmpty() }?.let { list ->
            put("attachments", JSONArray().apply { list.forEach { put(it.toJson()) } })
        }
        appVersion?.takeIf { it.isNotEmpty() }?.let { put("appVersion", it) }
    }

    companion object {
        fun from(json: JSONObject): ThreadRequest = ThreadRequest(
            category = FeedbackCategory.from(json.optString("category")) ?: FeedbackCategory.FEEDBACK,
            body = json.optString("body"),
            rating = if (json.isNull("rating")) null else json.optInt("rating").takeIf { it > 0 },
            pageContext = ScreenContext.from(json.getJSONObject("pageContext")),
            visitor = FeedbackVisitor.from(json.optJSONObject("visitor")),
            metadata = json.optJSONObject("metadata")?.let { context ->
                context.keys().asSequence().associateWith { key ->
                    if (context.isNull(key)) null else context.get(key)
                }
            },
            attachments = json.optJSONArray("attachments")?.let { array ->
                (0 until array.length()).map { Attachment.from(array.getJSONObject(it)) }
            },
            appVersion = json.optStringOrNull("appVersion"),
        )
    }
}

/**
 * Why a client was turned away. Logged once in a debug build and never shown
 * to the person holding the phone, who misconfigured nothing.
 */
enum class Refusal(val wire: String, val advice: String) {
    NOT_AN_APP("not-an-app", "The request did not declare itself an app."),
    MOBILE_NOT_ENABLED(
        "mobile-not-enabled",
        "This project takes no mobile feedback yet. Add the app under Mobile apps.",
    ),
    APP_NOT_ALLOWED("app-not-allowed", "This package name is not on the project's list of apps."),
    VISITOR_NOT_IDENTIFIED(
        "visitor-not-identified",
        "This project only takes feedback from identified people. Call identify() first.",
    ),
    VISITOR_NOT_VERIFIED(
        "visitor-not-verified",
        "This project requires a signature. Have your server sign the id and pass it as userHash.",
    ),
    VISITOR_NOT_LISTED("visitor-not-listed", "This visitor is not on the project's allow list.");

    companion object {
        fun from(value: String?): Refusal? = entries.firstOrNull { it.wire == value }
    }
}

/** Which ways in the panel offers. An app is given one. */
data class Actions(val point: Boolean, val record: Boolean, val feedback: Boolean)

/**
 * How the owner wants the widget to look. Light and dark is deliberately not
 * here: the sheet is native UI inside someone else's app, so that is the
 * device's call and the host app's, never a dashboard's.
 */
data class Appearance(
    val color: String,
    val size: String,
    val position: String,
    val rating: Boolean,
    val welcome: String,
    val label: String,
    val icon: String,
    val actions: Actions,
    val bubble: Boolean,
    val bubbleSeconds: Int,
) {
    companion object {
        fun from(json: JSONObject): Appearance {
            val actions = json.optJSONObject("actions") ?: JSONObject()
            return Appearance(
                color = json.optString("color", "#0f6e56"),
                size = json.optString("size", "medium"),
                position = json.optString("position", "bottom-right"),
                rating = json.optBoolean("rating", true),
                welcome = json.optString("welcome", ""),
                label = json.optString("label", "Feedback").ifEmpty { "Feedback" },
                icon = json.optString("icon", ""),
                actions = Actions(
                    point = actions.optBoolean("point"),
                    record = actions.optBoolean("record"),
                    feedback = actions.optBoolean("feedback", true),
                ),
                bubble = json.optBoolean("bubble", true),
                bubbleSeconds = json.optInt("bubbleSeconds"),
            )
        }
    }
}

data class ConfigResponse(
    val projectName: String,
    /** False when this client may not run the widget. Never an error. */
    val enabled: Boolean,
    val reason: Refusal?,
    val appearance: Appearance,
) {
    companion object {
        fun from(json: JSONObject): ConfigResponse = ConfigResponse(
            projectName = json.optString("projectName"),
            enabled = json.optBoolean("enabled"),
            reason = Refusal.from(json.optStringOrNull("reason")),
            appearance = Appearance.from(json.getJSONObject("appearance")),
        )
    }
}

/** `optString` answers "" for a missing key, which is not the same as absent. */
internal fun JSONObject.optStringOrNull(key: String): String? =
    if (isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }
