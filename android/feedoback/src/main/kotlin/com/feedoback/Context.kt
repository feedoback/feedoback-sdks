package com.feedoback

/**
 * What a host app attaches to every thread it opens from now on: a plan, a
 * tier, a feature flag — whatever the owner will want to read beside the
 * words. The web SDK's `setContext` and this are the same thing.
 *
 * The bounds mirror `customContextSchema` on the server exactly, and follow
 * its rule: keep what is valid and drop what is not, rather than refusing the
 * lot. A mistake in one key should not cost the visitor their feedback, and
 * applying the bounds here as well means a thread is never turned away for
 * something the SDK could see on the device.
 */
object CustomContext {
    const val MAX_KEYS = 30
    const val MAX_KEY_LENGTH = 80
    const val MAX_VALUE_LENGTH = 500

    /**
     * Drops what the server would refuse and keeps the rest.
     *
     * Keys are considered in sorted order, so which thirty survive is the same
     * on every launch rather than whatever the map happened to iterate.
     */
    fun bounded(context: Map<String, Any?>): Map<String, Any?> {
        val kept = LinkedHashMap<String, Any?>()
        for (key in context.keys.sorted()) {
            if (kept.size >= MAX_KEYS) break
            if (key.isEmpty() || key.length > MAX_KEY_LENGTH) continue
            when (val value = context[key]) {
                null -> kept[key] = null
                is String -> kept[key] = truncate(value)
                is Boolean -> kept[key] = value
                // The server takes finite numbers only. An infinity is a fault
                // in the app's own arithmetic, not something to file feedback
                // under — and JSONObject throws on one rather than sending it.
                is Number -> if (value.toDouble().isFinite()) kept[key] = value
                // Anything else — a list, a map, an object of the app's own —
                // has no shape the server stores, and stringifying it would
                // file a memory address under someone's feedback.
                else -> Unit
            }
        }
        return kept
    }

    /**
     * Trimmed to what the server counts, which is UTF-16 units on both sides
     * here. Backing off one unit when the cut lands on a high surrogate is
     * what stops a line of emoji ending in half of one.
     */
    private fun truncate(text: String): String {
        if (text.length <= MAX_VALUE_LENGTH) return text
        val end = if (Character.isHighSurrogate(text[MAX_VALUE_LENGTH - 1])) {
            MAX_VALUE_LENGTH - 1
        } else {
            MAX_VALUE_LENGTH
        }
        return text.substring(0, end)
    }
}
