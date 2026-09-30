package com.feedoback

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.graphics.Rect
import android.view.View
import java.lang.ref.WeakReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The whole SDK, from an app's point of view.
 *
 * Four calls: [start] once at launch, [present] from a control the app already
 * owns, [identify] when the app knows who this is, and [reset] on sign-out.
 * Everything else is the owner's, and arrives from the server.
 *
 * Every entry point is total. A bad key, no network, a project that has not
 * opened its mobile channel — none of it throws into somebody's
 * `Application.onCreate`. It logs once and goes quiet, which is the native
 * reading of a misconfigured widget removing itself.
 */
object Feedoback {
    const val VERSION = "0.1.0"

    private var configuration: FeedobackConfiguration? = null
    private var transport: Transport? = null
    private var store: Store? = null
    private var session: Session? = null
    private var log = Log(LogLevel.WARNING)
    private val scope: CoroutineScope = MainScope()

    /** What the server last said. Nothing is drawn until this arrives enabled. */
    private var config: ConfigResponse? = null
    private var launcher: LauncherView? = null
    @Volatile
    private var activityRef: WeakReference<Activity>? = null
    private var sheetIsUp = false
    private var knowsTheVisitor = false

    // Written on whichever thread the app calls from and read off the main
    // thread when a thread is sent, so all three are published rather than
    // left to a happens-before that nothing here establishes.

    /** Where the visitor is, as the app says. Set it when a screen appears. */
    @Volatile
    private var route = "/"

    @Volatile
    private var screenTitle = ""

    /** What the app attaches to every thread from now on. Bounded on the way
     *  in rather than on the way out, so an app that sets it once does not pay
     *  for the check on every send. */
    @Volatile
    private var context: Map<String, Any?> = emptyMap()

    /**
     * Call once, at launch. Calling it twice does not stack two launchers, the
     * way a snippet pasted twice must not stack two on a page.
     *
     * An app that starts from `Application.onCreate` needs nothing for
     * [activity]: the lifecycle callbacks registered here see every Activity
     * from the first one. An app that starts later — which is what React
     * Native and Flutter do, because their start call comes from their own
     * runtime once a screen is already up — has to name the Activity in
     * front, because `ActivityLifecycleCallbacks` never replays a resume that
     * has already happened and the launcher would wait for the next one.
     */
    @JvmStatic
    @JvmOverloads
    fun start(
        application: Application,
        configuration: FeedobackConfiguration,
        activity: Activity? = null,
    ) {
        if (this.configuration != null) {
            log.debug("already started; ignoring the second call")
            return
        }
        if (!configuration.projectKey.startsWith("pk_")) {
            log.error("that does not look like a project key; Feedoback is off")
            return
        }

        this.configuration = configuration
        this.log = Log(configuration.logLevel)

        val store = Store(SharedPreferencesStore(application))
        this.store = store

        val transport = Transport(
            host = configuration.host,
            projectKey = configuration.projectKey,
            identity = ClientIdentity(
                bundleId = Device.bundleId(application),
                installId = store.installId(),
                sdkVersion = VERSION,
            ),
        )
        this.transport = transport

        val appContext = application.applicationContext
        this.session = Session(
            transport = transport,
            store = store,
            context = { Device.screenContext(appContext, route, screenTitle) },
            metadata = { context },
            log = log,
        )

        application.registerActivityLifecycleCallbacks(Lifecycle)
        activity?.let { activityRef = WeakReference(it) }

        scope.launch {
            refreshConfig()
            // Anything written in a tunnel goes out now.
            session?.flushQueue()
        }
    }

    /**
     * Asks whether this client may run at all, and how the owner wants it to
     * look. A refusal is not an error: the launcher simply does not appear.
     */
    private suspend fun refreshConfig() {
        val transport = transport ?: return
        val visitor = store?.visitor()
        knowsTheVisitor = !visitor?.email.isNullOrEmpty()

        try {
            val answer = transport.config(visitor)
            config = answer
            withContext(Dispatchers.Main) {
                if (answer.enabled) {
                    showLauncherIfAsked()
                } else {
                    hideLauncher()
                    answer.reason?.let { log.warning(it.advice) }
                }
            }
        } catch (error: TransportFailure.UnknownProject) {
            log.error("that project key is not one this server knows; Feedoback is off")
        } catch (error: Exception) {
            // No network at launch is ordinary. Try again on the next
            // foreground rather than saying anything alarming.
            log.debug("could not reach the server yet")
        }
    }

    /**
     * Who the app says the visitor is.
     *
     * Declared, never authoritative — it is the app's own word. Pass
     * `userHash` when the project asks for signatures and your server has
     * computed one; that is the only part of it the server can check.
     */
    @JvmStatic
    fun identify(visitor: FeedbackVisitor) {
        val store = store ?: run {
            log.warning("identify() before start(); it was ignored")
            return
        }
        store.setVisitor(visitor)
        scope.launch { refreshConfig() }
    }

    /**
     * What an app calls on sign-out. Forgets the person and anything the app
     * attached about them; keeps the device. Threads already queued keep the
     * identity and context they were written with — the visitor did ask to
     * send those.
     */
    @JvmStatic
    fun reset() {
        context = emptyMap()
        store?.reset()
        scope.launch { refreshConfig() }
    }

    /**
     * Attaches custom context to every thread opened from now on: the plan
     * someone is on, the flag they have, the tier they bought. The same call
     * as the web SDK's, and the same limits — at most thirty keys, and what
     * exceeds them is dropped rather than costing the visitor their feedback.
     *
     * It is not persisted. An app sets this from state it already holds, and
     * carrying a stale plan across a launch would be worse than carrying none.
     */
    @JvmStatic
    fun setContext(context: Map<String, Any?>) {
        this.context = CustomContext.bounded(context)
    }

    /**
     * Names the screen the visitor is on, which is what feedback is filed
     * under. A path reads best — "checkout/payment" — because the dashboard
     * folds identifiers out of it the way it does a website's.
     */
    @JvmStatic
    @JvmOverloads
    fun setScreen(route: String, title: String = "") {
        this.route = route
        this.screenTitle = title
    }

    /**
     * Opens the sheet from a control the app already owns: a Settings row, a
     * menu item. Does nothing, loudly in a debug build, when the project has
     * not allowed this client.
     */
    @JvmStatic
    @JvmOverloads
    fun present(category: FeedbackCategory = FeedbackCategory.FEEDBACK) {
        onMain { show(category) }
    }

    private fun show(category: FeedbackCategory) {
        val configuration = configuration ?: run {
            log.warning("present() before start(); nothing to show")
            return
        }
        val answer = config
        if (answer == null || !answer.enabled) {
            log.warning(answer?.reason?.advice ?: "this client may not send feedback yet")
            return
        }
        if (sheetIsUp) return
        val activity = activityRef?.get() ?: run {
            log.warning("no activity to show the sheet from")
            return
        }
        val session = session ?: return

        // Claimed before the picture is taken, not after: capturing a
        // composited window takes a frame, and a second tap in that frame
        // would otherwise open a second sheet.
        sheetIsUp = true
        launcher?.setHidden(true)

        // Taken before the sheet is up, so the picture is the screen the
        // visitor is complaining about rather than the sheet covering it.
        if (configuration.screenshots == Screenshots.AUTOMATIC) {
            // The button is named here as well as hidden above, because
            // setHidden fades it over 150ms and the capture happens in the
            // first of them. Naming it takes it out of the draw outright.
            Screenshot.capture(activity, listOfNotNull(launcher?.button)) { bytes ->
                open(activity, answer, configuration, category, session, bytes)
            }
        } else {
            open(activity, answer, configuration, category, session, null)
        }
    }

    private fun open(
        activity: Activity,
        answer: ConfigResponse,
        configuration: FeedobackConfiguration,
        category: FeedbackCategory,
        session: Session,
        screenshot: ByteArray?,
    ) {
        FeedbackSheet(
            activity = activity,
            appearance = answer.appearance,
            categories = configuration.categories,
            category = category,
            knowsTheVisitor = knowsTheVisitor,
            screenshot = screenshot,
            scope = scope,
            onSend = { draft -> session.send(draft) },
            onClose = {
                sheetIsUp = false
                launcher?.setHidden(false)
                null
            },
        ).show()
    }

    /**
     * Marks a view whose contents must never leave the device. Password fields
     * need no marking — they are found on their own, because forgetting one of
     * those is the expensive mistake.
     */
    @JvmStatic
    fun redact(view: View) = Screenshot.redact(view)

    @JvmStatic
    fun unredact(view: View) = Screenshot.unredact(view)

    /**
     * Marks regions of the screen rather than views, for an interface that has
     * no view to hand over: a canvas, a game, or Flutter, which draws
     * everything it has into one.
     *
     * In the root view's own pixels, and replacing whatever was marked before —
     * the caller is the only thing that knows where all of them are.
     */
    @JvmStatic
    fun setRedactedRegions(regions: List<Rect>) = Screenshot.setRedactedRegions(regions)

    /** Takes the launcher out of the way for a screen that wants none: a video
     *  player, a camera. */
    @JvmStatic
    fun setLauncherHidden(hidden: Boolean) {
        onMain { launcher?.setHidden(hidden) }
    }

    /**
     * Anything that touches a view, on the thread Android insists on.
     *
     * A Kotlin app calls these from a click listener and is already there, so
     * the common case stays synchronous — which matters for [present], where
     * the screenshot has to be taken before anything else on screen moves. A
     * bridge does not: React Native runs a Turbo Module method on its own
     * native-modules thread, and Flutter its platform thread. On iOS the
     * compiler settles this by making the whole class @MainActor; Kotlin has
     * no such annotation, so the SDK does it rather than asking every bridge
     * to remember.
     */
    private inline fun onMain(crossinline body: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            body()
        } else {
            Handler(Looper.getMainLooper()).post { body() }
        }
    }

    private fun showLauncherIfAsked() {
        val configuration = configuration ?: return
        if (!configuration.launcher.enabled) return
        val answer = config ?: return
        val activity = activityRef?.get() ?: return
        if (launcher != null) return

        launcher = LauncherView(activity, configuration.launcher, answer.appearance) {
            present()
        }.also { it.attach() }
    }

    private fun hideLauncher() {
        launcher?.detach()
        launcher = null
    }

    /**
     * The launcher belongs to whichever Activity is in front, so it follows
     * one to the next rather than being added to each screen by hand.
     */
    private object Lifecycle : Application.ActivityLifecycleCallbacks {
        override fun onActivityResumed(activity: Activity) {
            activityRef = WeakReference(activity)
            hideLauncher()
            showLauncherIfAsked()
            scope.launch { session?.flushQueue() }
        }

        override fun onActivityPaused(activity: Activity) {
            if (activityRef?.get() === activity) hideLauncher()
        }

        override fun onActivityCreated(activity: Activity, state: Bundle?) {}
        override fun onActivityStarted(activity: Activity) {}
        override fun onActivityStopped(activity: Activity) {}
        override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) {}
        override fun onActivityDestroyed(activity: Activity) {}
    }
}

/** The only place SharedPreferences is touched, so the store stays testable. */
internal class SharedPreferencesStore(context: Context) : Preferences {
    private val preferences =
        context.getSharedPreferences("com.feedoback", Context.MODE_PRIVATE)

    override fun getString(key: String): String? = preferences.getString(key, null)

    override fun putString(key: String, value: String?) {
        preferences.edit().apply {
            if (value == null) remove(key) else putString(key, value)
        }.apply()
    }
}
