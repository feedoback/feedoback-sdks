package com.feedoback

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * One screen: say the thing, and send it.
 *
 * A bottom sheet built in code rather than from XML layouts, for the same
 * reason the SDK has no dependencies: a library that ships resources collides
 * with a customer's, and one that ships none cannot.
 */
internal class FeedbackSheet(
    private val activity: Activity,
    private val appearance: Appearance,
    private val categories: List<FeedbackCategory>,
    category: FeedbackCategory,
    private val knowsTheVisitor: Boolean,
    private val screenshot: ByteArray?,
    private val scope: CoroutineScope,
    private val onSend: suspend (Draft) -> SendOutcome,
    private val onClose: () -> Void?,
) : Dialog(activity, android.R.style.Theme_DeviceDefault_Light_NoActionBar) {

    private var draft = Draft(category = category, screenshot = screenshot)
    private val accent = Palette.accent(appearance.color)

    private lateinit var message: EditText
    private lateinit var email: EditText
    private lateinit var send: Button
    private lateinit var status: TextView
    private lateinit var starsRow: LinearLayout
    private lateinit var screenshotRow: LinearLayout
    private lateinit var panel: LinearLayout
    private lateinit var scrim: View
    private val stars = mutableListOf<TextView>()
    private var busy = false
    private var leaving = false

    private fun dp(value: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), activity.resources.displayMetrics
    ).toInt()

    private val surface: Int
        get() = if (isDark) Color.rgb(28, 28, 30) else Color.WHITE
    private val onSurface: Int
        get() = if (isDark) Color.rgb(242, 242, 247) else Color.rgb(18, 18, 18)
    private val muted: Int
        get() = if (isDark) Color.rgb(142, 142, 147) else Color.rgb(110, 110, 115)
    private val field: Int
        get() = if (isDark) Color.rgb(44, 44, 46) else Color.rgb(242, 242, 247)

    private val isDark: Boolean
        get() = activity.resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // The sheet is the panel, and the window is the whole screen behind
        // it. A bottom-gravity window sized to its content would be cheaper,
        // but then there is nowhere to draw a scrim, nothing to slide the
        // panel over, and no room to drag it down — which is what tells
        // somebody this is a sheet rather than a page that grew.
        panel = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(surface)
                cornerRadii = floatArrayOf(
                    dp(20).toFloat(), dp(20).toFloat(), dp(20).toFloat(), dp(20).toFloat(),
                    0f, 0f, 0f, 0f,
                )
            }
            // Off the bottom until the entrance runs. Set from the screen
            // height so it is already offstage on the first frame rather than
            // one frame after it.
            translationY = activity.resources.displayMetrics.heightPixels.toFloat()
        }
        val handle = buildHandle()
        panel.addView(handle)
        panel.addView(buildLayout())

        scrim = View(activity).apply {
            setBackgroundColor(Color.BLACK)
            alpha = 0f
            // Tapping the app behind a sheet closes it, the way tapping
            // outside one on iOS does.
            contentDescription = "Close"
            setOnClickListener { leave() }
        }

        setContentView(
            FrameLayout(activity).apply {
                addView(
                    scrim,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
                    ),
                )
                addView(
                    panel,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        Gravity.BOTTOM,
                    ),
                )
            }
        )

        window?.apply {
            // The scrim is ours, so the system must not draw a second one.
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }

        attachDrag(handle)
        panel.post { enter() }

        setOnDismissListener { onClose() }
        updateSend()
    }

    /** How dark the app behind gets. Enough to say "this is modal", not so
     *  much that the screen being complained about is unreadable. */
    private val scrimAlpha = 0.4f

    /**
     * The grabber, and the only thing that drags.
     *
     * iOS asks `UISheetPresentationController` for one and gets the gesture
     * with it; here both are drawn. Dragging is kept to this strip rather than
     * the whole panel because the panel is mostly a text field, and a sheet
     * that closes when somebody swipes to select a word is worse than one that
     * does not drag at all.
     */
    private fun buildHandle(): View {
        val handle = FrameLayout(activity).apply {
            setPadding(0, dp(8), 0, dp(4))
        }
        handle.addView(
            View(activity).apply {
                background = GradientDrawable().apply {
                    setColor(if (isDark) Color.rgb(72, 72, 74) else Color.rgb(209, 209, 214))
                    cornerRadius = dp(3).toFloat()
                }
            },
            FrameLayout.LayoutParams(dp(36), dp(5), Gravity.CENTER_HORIZONTAL),
        )
        return handle
    }

    /** Whether the device has animations turned off, which is Android's way of
     *  saying what `prefers-reduced-motion` says on the web. */
    private val animationScale: Float
        get() = Settings.Global.getFloat(
            activity.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f
        )

    private fun enter() {
        if (animationScale == 0f) {
            panel.translationY = 0f
            scrim.alpha = scrimAlpha
            return
        }
        panel.animate().translationY(0f).setDuration(240)
            .setInterpolator(DecelerateInterpolator()).start()
        scrim.animate().alpha(scrimAlpha).setDuration(240).start()
    }

    /** Closing, with the panel going back the way it came. */
    private fun leave() {
        if (leaving) return
        leaving = true
        if (animationScale == 0f) {
            dismiss()
            return
        }
        scrim.animate().alpha(0f).setDuration(180).start()
        panel.animate().translationY(panel.height.toFloat()).setDuration(180)
            .withEndAction { if (isShowing) dismiss() }.start()
    }

    /**
     * Drag down to close, and let go short of the threshold to settle back.
     *
     * The scrim fades with the panel, so a drag that is abandoned halfway
     * looks like one gesture rather than two things moving separately.
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun attachDrag(handle: View) {
        var startY = 0f
        var startTranslation = 0f

        handle.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startY = event.rawY
                    startTranslation = panel.translationY
                    panel.animate().cancel()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dragged = (startTranslation + event.rawY - startY).coerceAtLeast(0f)
                    panel.translationY = dragged
                    val height = panel.height.takeIf { it > 0 } ?: 1
                    scrim.alpha = scrimAlpha * (1f - dragged / height).coerceIn(0f, 1f)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    // A third of the way down is far enough to mean it.
                    if (panel.translationY > panel.height * 0.3f) {
                        leave()
                    } else {
                        panel.animate().translationY(0f).setDuration(160).start()
                        scrim.animate().alpha(scrimAlpha).setDuration(160).start()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun buildLayout(): View {
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
        }

        root.addView(TextView(activity).apply {
            text = appearance.welcome.ifEmpty { "Send feedback" }
            textSize = 18f
            setTextColor(onSurface)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })

        // A control with one option is not a choice, so it only appears when
        // the project offers more than one thing to send.
        if (categories.size > 1) root.addView(buildCategories())

        // Stars on feedback only: a bug report is not an experience to rate.
        starsRow = buildStars()
        starsRow.visibility =
            if (appearance.rating && draft.category == FeedbackCategory.FEEDBACK) View.VISIBLE
            else View.GONE
        root.addView(starsRow)

        message = EditText(activity).apply {
            hint = "What could be better?"
            setHintTextColor(muted)
            setTextColor(onSurface)
            gravity = Gravity.TOP or Gravity.START
            minLines = 4
            maxLines = 8
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = GradientDrawable().apply {
                setColor(field)
                cornerRadius = dp(10).toFloat()
            }
            contentDescription = "Your feedback"
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(text: Editable?) {
                    draft = draft.copy(body = text?.toString() ?: "")
                    updateSend()
                }
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            })
        }
        root.addView(message, rowParams(dp(16)))

        email = EditText(activity).apply {
            hint = "Your email, to hear back"
            setHintTextColor(muted)
            setTextColor(onSurface)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = GradientDrawable().apply {
                setColor(field)
                cornerRadius = dp(10).toFloat()
            }
            contentDescription = "Your email"
            visibility = if (knowsTheVisitor) View.GONE else View.VISIBLE
        }
        root.addView(email, rowParams(dp(12)))

        screenshotRow = buildScreenshot()
        root.addView(screenshotRow, rowParams(dp(16)))

        status = TextView(activity).apply {
            setTextColor(muted)
            textSize = 13f
            visibility = View.GONE
        }
        root.addView(status, rowParams(dp(12)))

        send = Button(activity).apply {
            text = "Send"
            isAllCaps = false
            textSize = 16f
            setTextColor(Palette.onAccent(accent))
            background = GradientDrawable().apply {
                setColor(accent)
                cornerRadius = dp(12).toFloat()
            }
            setOnClickListener { submit() }
        }
        root.addView(send, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(52)
        ).apply { topMargin = dp(16) })

        return root
    }

    private fun rowParams(top: Int) = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply { topMargin = top }

    private fun buildCategories(): View {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            background = GradientDrawable().apply {
                setColor(field)
                cornerRadius = dp(10).toFloat()
            }
            setPadding(dp(3), dp(3), dp(3), dp(3))
        }

        categories.forEach { category ->
            val chip = TextView(activity).apply {
                text = titleFor(category)
                gravity = Gravity.CENTER
                setPadding(dp(10), dp(10), dp(10), dp(10))
                contentDescription = titleFor(category)
                setOnClickListener { choose(category, row) }
            }
            row.addView(chip, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        paintCategories(row)
        return row.also { it.layoutParams = rowParams(dp(16)) }
    }

    private fun titleFor(category: FeedbackCategory) = when (category) {
        FeedbackCategory.FEEDBACK -> "Feedback"
        FeedbackCategory.BUG -> "Problem"
        FeedbackCategory.IDEA -> "Idea"
    }

    private fun choose(category: FeedbackCategory, row: LinearLayout) {
        draft = draft.copy(category = category)
        message.hint =
            if (category == FeedbackCategory.BUG) "What went wrong?" else "What could be better?"
        // Stars belong to feedback, so they go when it is no longer that.
        val ratable = appearance.rating && category == FeedbackCategory.FEEDBACK
        starsRow.visibility = if (ratable) View.VISIBLE else View.GONE
        if (!ratable) {
            draft = draft.copy(rating = null)
            paintStars()
        }
        paintCategories(row)
        updateSend()
    }

    private fun paintCategories(row: LinearLayout) {
        categories.forEachIndexed { index, category ->
            val chip = row.getChildAt(index) as TextView
            val selected = category == draft.category
            chip.setTextColor(if (selected) Palette.onAccent(accent) else onSurface)
            chip.background = if (selected) {
                GradientDrawable().apply {
                    setColor(accent)
                    cornerRadius = dp(8).toFloat()
                }
            } else {
                null
            }
            chip.isSelected = selected
        }
    }

    private fun buildStars(): LinearLayout {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = rowParams(dp(16))
            contentDescription = "Rating"
        }
        for (value in 1..5) {
            val star = TextView(activity).apply {
                text = "☆"
                textSize = 28f
                setTextColor(muted)
                setPadding(dp(4), 0, dp(10), 0)
                contentDescription = "$value out of 5"
                setOnClickListener {
                    draft = draft.copy(rating = if (draft.rating == value) null else value)
                    paintStars()
                    updateSend()
                }
            }
            stars.add(star)
            row.addView(star)
        }
        return row
    }

    private fun paintStars() {
        stars.forEachIndexed { index, star ->
            val filled = index + 1 <= (draft.rating ?: 0)
            star.text = if (filled) "★" else "☆"
            star.setTextColor(if (filled) accent else muted)
            star.isSelected = filled
        }
    }

    private fun buildScreenshot(): LinearLayout {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = if (screenshot == null) View.GONE else View.VISIBLE
        }
        if (screenshot == null) return row

        row.addView(ImageView(activity).apply {
            setImageBitmap(BitmapFactory.decodeByteArray(screenshot, 0, screenshot.size))
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "The screen this will be sent with"
        }, LinearLayout.LayoutParams(dp(40), dp(72)))

        row.addView(TextView(activity).apply {
            text = "This screen is attached"
            setTextColor(muted)
            textSize = 14f
            setPadding(dp(12), 0, dp(12), 0)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        // The strongest privacy guarantee available, and it costs nothing:
        // nothing is sent that the visitor did not look at and keep.
        row.addView(TextView(activity).apply {
            text = "Remove"
            setTextColor(muted)
            textSize = 14f
            contentDescription = "Remove the screen"
            setOnClickListener {
                draft = draft.copy(screenshot = null)
                row.visibility = View.GONE
            }
        })
        return row
    }

    private fun updateSend() {
        val ready = draft.hasSomethingToSay && !busy
        send.isEnabled = ready
        send.alpha = if (ready) 1f else 0.5f
    }

    private fun submit() {
        if (!draft.hasSomethingToSay || busy) return
        draft = draft.copy(body = message.text.toString(), email = email.text.toString())
        setBusy(true)

        scope.launch {
            val outcome = onSend(draft)
            withContext(Dispatchers.Main) {
                setBusy(false)
                when (outcome) {
                    is SendOutcome.Sent -> finish("Thank you. Sent.")
                    // Honest rather than cheerful: it is not there yet, and the
                    // visitor should not have to wonder later.
                    SendOutcome.Queued -> finish("No connection. This will send itself later.")
                    else -> show("That could not be sent. Try again in a moment.")
                }
            }
        }
    }

    private fun setBusy(value: Boolean) {
        busy = value
        send.text = if (value) "Sending…" else "Send"
        message.isEnabled = !value
        email.isEnabled = !value
        updateSend()
    }

    private fun show(text: String) {
        status.text = text
        status.visibility = View.VISIBLE
        status.announceForAccessibility(text)
    }

    private fun finish(text: String) {
        show(text)
        // Long enough to read, short enough not to trap anyone in a sheet
        // they are done with.
        status.postDelayed({ if (isShowing) leave() }, 1400)
    }
}
