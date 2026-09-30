package com.feedoback.example

import android.app.Application
import com.feedoback.FeedbackCategory
import com.feedoback.FeedbackVisitor
import com.feedoback.Feedoback
import com.feedoback.FeedobackConfiguration
import com.feedoback.LauncherOptions
import com.feedoback.LauncherStyle
import com.feedoback.LogLevel
import com.feedoback.Screenshots
import com.feedoback.Theme

/**
 * The smallest app that exercises the SDK the way a customer would: a
 * Settings-style list with a row that opens the sheet, plus the floating
 * launcher, an identify, and a screen name.
 *
 * Reads its project from the build config so the same APK can be pointed at a
 * dev server, which is what the verification script does.
 */
class ExampleApp : Application() {
    override fun onCreate() {
        super.onCreate()

        val settings = getSharedPreferences("example", MODE_PRIVATE)
        val key = settings.getString("key", null) ?: "pk_missing"
        val host = settings.getString("host", null) ?: "http://10.0.2.2:3000"

        Feedoback.start(
            this,
            FeedobackConfiguration(
                projectKey = key,
                host = host,
                theme = Theme.SYSTEM,
                categories = listOf(
                    FeedbackCategory.FEEDBACK, FeedbackCategory.BUG, FeedbackCategory.IDEA
                ),
                screenshots = Screenshots.AUTOMATIC,
                launcher = LauncherOptions(enabled = true, style = LauncherStyle.LABELLED),
                logLevel = LogLevel.DEBUG,
            ),
        )

        Feedoback.setScreen("settings", "Settings")

        settings.getString("user", null)?.let { id ->
            Feedoback.identify(
                FeedbackVisitor(
                    id = id,
                    email = settings.getString("email", null),
                    name = "Ada Lovelace",
                    userHash = settings.getString("hash", null),
                )
            )
        }
    }
}
