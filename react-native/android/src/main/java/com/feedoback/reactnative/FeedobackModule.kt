package com.feedoback.reactnative

import android.app.Application
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.module.annotations.ReactModule
import com.feedoback.FeedbackCategory
import com.feedoback.FeedbackVisitor
import com.feedoback.Feedoback
import com.feedoback.FeedobackConfiguration

/**
 * The bridge to the Android SDK, and nothing more.
 *
 * Every method here is one call to [Feedoback] with a ReadableMap turned into
 * something it takes. It holds no state of its own: the SDK is an object with
 * a lifetime of the process, which is also what a React Native reload leaves
 * standing, so a module that cached anything would be caching a copy of the
 * truth.
 */
@ReactModule(name = FeedobackModule.NAME)
class FeedobackModule(private val context: ReactApplicationContext) :
    NativeFeedobackSpec(context) {

    override fun getName(): String = NAME

    override fun start(options: ReadableMap?) {
        val application = context.applicationContext as? Application ?: return
        // Read by the SDK rather than here: Flutter hands over the same map,
        // and a second reading of it is a second thing to keep in step.
        val configuration = FeedobackConfiguration.from(options?.toHashMap().orEmpty()) ?: return
        // The Activity in front, because start() arrives from JavaScript with a
        // screen already up and the lifecycle callbacks never replay a resume.
        Feedoback.start(application, configuration, context.currentActivity)
    }

    override fun present(category: String) {
        val chosen = FeedbackCategory.from(category)
        // Native's own default when JavaScript named nothing, rather than a
        // second default chosen here.
        if (chosen == null) Feedoback.present() else Feedoback.present(chosen)
    }

    override fun identify(visitor: ReadableMap?) {
        val fields = visitor?.toHashMap().orEmpty()
        Feedoback.identify(
            FeedbackVisitor(
                id = fields["id"] as? String,
                email = fields["email"] as? String,
                name = fields["name"] as? String,
                userHash = fields["userHash"] as? String,
            ),
        )
    }

    override fun setContext(context: ReadableMap?) {
        Feedoback.setContext(context?.toHashMap().orEmpty())
    }

    override fun setScreen(route: String, title: String) {
        Feedoback.setScreen(route, title)
    }

    override fun setLauncherHidden(hidden: Boolean) {
        Feedoback.setLauncherHidden(hidden)
    }

    override fun reset() {
        Feedoback.reset()
    }

    companion object {
        const val NAME = "Feedoback"
    }
}
