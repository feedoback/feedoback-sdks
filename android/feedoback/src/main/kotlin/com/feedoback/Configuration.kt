package com.feedoback

/**
 * What the host app hands [Feedoback.start].
 *
 * Everything the owner controls — the accent, the word on the launcher,
 * whether stars are asked for — comes from the server, so it can change
 * without an app release. Everything only the app can know is here.
 */
data class FeedobackConfiguration(
    /** The project's public key, `pk_…`. Readable by anyone who unzips the
     *  APK: it identifies a project and grants nothing. */
    val projectKey: String,
    /** Where the app is deployed. */
    val host: String = DEFAULT_HOST,
    /** Light and dark. The sheet is native UI inside someone else's app, so
     *  this is the app's decision — the server is never asked and never tells. */
    val theme: Theme = Theme.SYSTEM,
    /** Which categories the sheet offers. With one it shows no picker, because
     *  a control with a single option is not a choice. */
    val categories: List<FeedbackCategory> = listOf(FeedbackCategory.FEEDBACK),
    /** Whether a picture of the screen rides along. The app knows which of its
     *  screens are sensitive; a dashboard does not. */
    val screenshots: Screenshots = Screenshots.AUTOMATIC,
    /** The floating button, off unless asked for. */
    val launcher: LauncherOptions = LauncherOptions(),
    /** How loud the SDK is. Never anything in a release build unless asked:
     *  this runs inside somebody else's product. */
    val logLevel: LogLevel = LogLevel.WARNING,
) {
    companion object {
        const val DEFAULT_HOST = "https://feedoback.com"

        /**
         * A configuration out of the loosely-typed map a bridge hands over.
         *
         * React Native and Flutter both arrive with one of these, and doing
         * the mapping in each of them would be doing it twice — and then
         * finding out, two releases later, that only one of them learned about
         * a new option. It lives here because this is where the enums are.
         *
         * Nothing here invents a default. A key that is absent or unrecognised
         * leaves the SDK's own default in place, which is the one a plain
         * Kotlin app gets. Null when there is no project key at all, which is
         * the one thing there is no falling back from.
         */
        @JvmStatic
        fun from(options: Map<String, Any?>): FeedobackConfiguration? {
            val projectKey = options.text("projectKey") ?: return null
            val defaults = FeedobackConfiguration(projectKey)

            return FeedobackConfiguration(
                projectKey = projectKey,
                host = options.text("host") ?: defaults.host,
                theme = named<Theme>(options["theme"]) ?: defaults.theme,
                categories = categories(options["categories"]) ?: defaults.categories,
                screenshots = named<Screenshots>(options["screenshots"]) ?: defaults.screenshots,
                launcher = launcher(options["launcher"]) ?: defaults.launcher,
                logLevel = named<LogLevel>(options["logLevel"]) ?: defaults.logLevel,
            )
        }

        /**
         * An enum by the name a bridge sends, which is the lower-case one the
         * TypeScript and Dart surfaces use. Matching on the name rather than a
         * table means an enum gaining a case does not need this to be edited.
         */
        private inline fun <reified T : Enum<T>> named(value: Any?): T? {
            val name = (value as? String)?.replace("-", "_") ?: return null
            return enumValues<T>().firstOrNull { it.name.equals(name, ignoreCase = true) }
        }

        private fun categories(value: Any?): List<FeedbackCategory>? {
            val names = value as? List<*> ?: return null
            val kept = names.mapNotNull { FeedbackCategory.from(it as? String) }.distinct()
            return kept.ifEmpty { null }
        }

        private fun launcher(value: Any?): LauncherOptions? {
            val options = value as? Map<*, *> ?: return null
            val defaults = LauncherOptions()
            val offset = options["offset"] as? Map<*, *>

            return LauncherOptions(
                enabled = options["enabled"] as? Boolean ?: defaults.enabled,
                corner = named<LauncherCorner>(options["corner"]) ?: defaults.corner,
                offsetX = (offset?.get("x") as? Number)?.toInt() ?: defaults.offsetX,
                offsetY = (offset?.get("y") as? Number)?.toInt() ?: defaults.offsetY,
                style = style(options["style"]) ?: defaults.style,
                draggable = options["draggable"] as? Boolean ?: defaults.draggable,
                hidesWithKeyboard = options["hidesWithKeyboard"] as? Boolean
                    ?: defaults.hidesWithKeyboard,
            )
        }

        /** The one name that is not the enum's own: "icon" reads better in an
         *  app's configuration than "iconOnly" does. */
        private fun style(value: Any?): LauncherStyle? = when (value) {
            "icon" -> LauncherStyle.ICON_ONLY
            "labelled" -> LauncherStyle.LABELLED
            else -> null
        }

        private fun Map<String, Any?>.text(key: String): String? =
            (this[key] as? String)?.trim()?.takeIf { it.isNotEmpty() }
    }
}

enum class Theme { SYSTEM, LIGHT, DARK }

enum class Screenshots {
    /** Captured when the sheet opens. The visitor always sees it first. */
    AUTOMATIC,
    /** Only when the visitor asks for one. */
    MANUAL,
    /** Never. */
    OFF,
}

enum class LauncherCorner { TOP_START, TOP_END, BOTTOM_START, BOTTOM_END }

enum class LauncherStyle { ICON_ONLY, LABELLED }

data class LauncherOptions(
    val enabled: Boolean = false,
    val corner: LauncherCorner = LauncherCorner.BOTTOM_END,
    /** From the window insets, never the raw edge. Density-independent pixels. */
    val offsetX: Int = 16,
    val offsetY: Int = 24,
    val style: LauncherStyle = LauncherStyle.ICON_ONLY,
    /** Dragging snaps it to the nearest edge on release. */
    val draggable: Boolean = true,
    /** Out of the way while somebody is typing. */
    val hidesWithKeyboard: Boolean = true,
)
