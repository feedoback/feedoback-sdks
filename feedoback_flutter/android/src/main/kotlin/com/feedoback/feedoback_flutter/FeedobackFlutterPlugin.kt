package com.feedoback.feedoback_flutter

import android.app.Activity
import android.app.Application
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import com.feedoback.CustomContext
import com.feedoback.FeedbackCategory
import com.feedoback.FeedbackVisitor
import com.feedoback.Feedoback
import com.feedoback.FeedobackConfiguration
import io.flutter.embedding.android.FlutterView
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result

/**
 * The bridge to the Android SDK, and nothing more.
 *
 * Every method here is one call to [Feedoback] with a channel argument turned
 * into something it takes. It holds no state of its own: the SDK is an object
 * with the lifetime of the process, which is also what a Flutter hot restart
 * leaves standing, so a plugin that cached anything would be caching a copy of
 * the truth.
 */
class FeedobackFlutterPlugin : FlutterPlugin, MethodCallHandler, ActivityAware {
    private lateinit var channel: MethodChannel
    private var application: Application? = null
    private var activity: Activity? = null

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        application = binding.applicationContext as? Application
        channel = MethodChannel(binding.binaryMessenger, CHANNEL)
        channel.setMethodCallHandler(this)
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
        application = null
    }

    override fun onMethodCall(call: MethodCall, result: Result) {
        when (call.method) {
            "start" -> start(call)
            "present" -> present(call.argument<String>("category"))
            "identify" -> identify(call)
            "setContext" -> Feedoback.setContext(CustomContext.bounded(call.map()))
            "setScreen" -> Feedoback.setScreen(
                call.argument<String>("route").orEmpty(),
                call.argument<String>("title").orEmpty(),
            )
            "setLauncherHidden" ->
                Feedoback.setLauncherHidden(call.argument<Boolean>("hidden") ?: false)
            "reset" -> Feedoback.reset()
            "setRedactedRegions" -> setRedactedRegions(call)
            else -> return result.notImplemented()
        }
        result.success(null)
    }

    private fun start(call: MethodCall) {
        val application = application ?: return
        val configuration = FeedobackConfiguration.from(call.map()) ?: return
        // The Activity in front, because start() arrives from Dart with a
        // screen already up and the lifecycle callbacks never replay a resume.
        Feedoback.start(application, configuration, activity)
    }

    private fun present(category: String?) {
        // Native's own default when Dart named nothing, rather than a second
        // default chosen here.
        val chosen = FeedbackCategory.from(category)
        if (chosen == null) Feedoback.present() else Feedoback.present(chosen)
    }

    private fun identify(call: MethodCall) {
        val fields = call.map()
        Feedoback.identify(
            FeedbackVisitor(
                id = fields["id"] as? String,
                email = fields["email"] as? String,
                name = fields["name"] as? String,
                userHash = fields["userHash"] as? String,
            ),
        )
    }

    /**
     * Where on screen must never leave the device.
     *
     * Flutter draws its whole interface into one view, so there is no view to
     * mark and Dart sends rectangles instead — in its own logical pixels,
     * relative to its own view. The SDK paints in the root view's pixels, so
     * both the density and where Flutter's view sits inside the window have to
     * be put back.
     */
    private fun setRedactedRegions(call: MethodCall) {
        val regions = call.argument<List<Map<String, Any?>>>("regions").orEmpty()
        val flutter = activity?.let { flutterViewIn(it.window.decorView) }
        val density = activity?.resources?.displayMetrics?.density ?: 1f
        val origin = IntArray(2).also { flutter?.getLocationInWindow(it) }

        Feedoback.setRedactedRegions(
            regions.mapNotNull { region ->
                val bounds = pixelBounds(region, density, origin[0], origin[1]) ?: return@mapNotNull null
                Rect(bounds[0], bounds[1], bounds[2], bounds[3])
            },
        )
    }

    /** The call's arguments, which are always a map on this channel. */
    private fun MethodCall.map(): Map<String, Any?> =
        arguments<Map<String, Any?>>() ?: emptyMap()

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        activity = binding.activity
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        activity = binding.activity
    }

    override fun onDetachedFromActivity() {
        activity = null
    }

    override fun onDetachedFromActivityForConfigChanges() {
        activity = null
    }

    companion object {
        const val CHANNEL = "com.feedoback/feedoback"

        /**
         * A rectangle Dart measured, in the pixels the screenshot pass draws
         * in: logical pixels times the density, offset by where Flutter's own
         * view starts in the window.
         *
         * Left, top, right, bottom — an array rather than a Rect so the rule
         * can be read and tested without an emulator, the same reason the
         * palette is arithmetic on integers.
         */
        @JvmStatic
        fun pixelBounds(
            region: Map<String, Any?>,
            density: Float,
            offsetX: Int,
            offsetY: Int,
        ): IntArray? {
            val left = (region["left"] as? Number)?.toFloat() ?: return null
            val top = (region["top"] as? Number)?.toFloat() ?: return null
            val width = (region["width"] as? Number)?.toFloat() ?: return null
            val height = (region["height"] as? Number)?.toFloat() ?: return null
            if (width <= 0f || height <= 0f) return null

            val x = Math.round(left * density) + offsetX
            val y = Math.round(top * density) + offsetY
            return intArrayOf(x, y, x + Math.round(width * density), y + Math.round(height * density))
        }

        /** Flutter's own view, wherever the embedding put it. */
        @JvmStatic
        fun flutterViewIn(root: View): View? {
            if (root is FlutterView) return root
            if (root !is ViewGroup) return null
            for (index in 0 until root.childCount) {
                flutterViewIn(root.getChildAt(index))?.let { return it }
            }
            return null
        }
    }
}
