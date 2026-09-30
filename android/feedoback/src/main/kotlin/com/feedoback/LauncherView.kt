package com.feedoback

import android.app.Activity
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView

/**
 * The floating button, added to the Activity's own content view.
 *
 * Deliberately not a `TYPE_APPLICATION_OVERLAY` window: that needs
 * SYSTEM_ALERT_WINDOW, which this SDK will not ask a customer's app to
 * request, and which draws over other apps. A view in the Activity draws over
 * nothing that is not already the host app's.
 */
internal class LauncherView(
    private val activity: Activity,
    private val options: LauncherOptions,
    appearance: Appearance,
    private val onTap: () -> Unit,
) {
    private val accent = Palette.accent(appearance.color)
    private val label = appearance.label.ifEmpty { "Feedback" }

    private var keyboardIsUp = false
    private var hiddenByHost = false
    private var downX = 0f
    private var downY = 0f
    private var moved = false

    private fun dp(value: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), activity.resources.displayMetrics
    ).toInt()

    val button: TextView = TextView(activity).apply {
        text = if (options.style == LauncherStyle.LABELLED) "💬  $label" else "💬"
        textSize = 15f
        setTextColor(Palette.onAccent(accent))
        gravity = Gravity.CENTER
        val horizontal = if (options.style == LauncherStyle.LABELLED) dp(20) else dp(16)
        setPadding(horizontal, dp(14), horizontal, dp(14))
        background = GradientDrawable().apply {
            setColor(accent)
            cornerRadius = dp(28).toFloat()
        }
        elevation = dp(6).toFloat()
        // The word is the accessible name whether or not it is drawn, so what
        // a voice-control user says matches what an owner chose.
        contentDescription = label
        isClickable = true
        isFocusable = true
    }

    private var container: ViewGroup? = null

    fun attach() {
        val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        if (button.parent != null) return

        val params = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = when (options.corner) {
                LauncherCorner.TOP_START -> Gravity.TOP or Gravity.START
                LauncherCorner.TOP_END -> Gravity.TOP or Gravity.END
                LauncherCorner.BOTTOM_START -> Gravity.BOTTOM or Gravity.START
                LauncherCorner.BOTTOM_END -> Gravity.BOTTOM or Gravity.END
            }
            // From the insets, never the raw edge: a button under the gesture
            // bar is one nobody can press.
            val insets = activity.window?.decorView?.rootWindowInsets
            val bottom = insets?.systemWindowInsetBottom ?: 0
            val top = insets?.systemWindowInsetTop ?: 0
            marginStart = dp(options.offsetX)
            marginEnd = dp(options.offsetX)
            bottomMargin = dp(options.offsetY) + bottom
            topMargin = dp(options.offsetY) + top
        }

        content.addView(button, params)
        container = content

        if (options.draggable) watchDrag()
        if (options.hidesWithKeyboard) watchKeyboard(content)
    }

    fun detach() {
        (button.parent as? ViewGroup)?.removeView(button)
        container = null
    }

    fun setHidden(hidden: Boolean) {
        hiddenByHost = hidden
        applyVisibility()
    }

    /**
     * Two things hide the button and they must not fight: the sheet being up
     * outlasts a keyboard that closes underneath it, so each is remembered and
     * the button is shown only when neither applies.
     */
    private fun applyVisibility() {
        val hidden = hiddenByHost || keyboardIsUp
        button.animate().alpha(if (hidden) 0f else 1f).setDuration(150).start()
        button.isClickable = !hidden
        button.isFocusable = !hidden
        button.importantForAccessibility =
            if (hidden) View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            else View.IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    private fun watchDrag() {
        button.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX - view.translationX
                    downY = event.rawY - view.translationY
                    moved = false
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (!moved && (kotlin.math.abs(dx - view.translationX) > dp(6) ||
                            kotlin.math.abs(dy - view.translationY) > dp(6))
                    ) {
                        moved = true
                    }
                    view.translationX = dx
                    view.translationY = dy
                    moved
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (moved) {
                        snapToNearestEdge(view)
                        true
                    } else {
                        // Not a drag: let the click through.
                        view.performClick()
                        true
                    }
                }
                else -> false
            }
        }
        button.setOnClickListener { onTap() }
    }

    /** Released mid-screen, it goes back to an edge: a button floating in the
     *  middle of someone's app is in the way of it. */
    private fun snapToNearestEdge(view: View) {
        val parent = view.parent as? ViewGroup ?: return
        val centre = view.x + view.width / 2f
        val target = if (centre < parent.width / 2f) {
            -(view.left.toFloat() - dp(options.offsetX))
        } else {
            (parent.width - view.right).toFloat() + dp(options.offsetX)
        }
        view.animate().translationX(view.translationX + target).setDuration(220).start()
    }

    private fun watchKeyboard(content: ViewGroup) {
        content.viewTreeObserver.addOnGlobalLayoutListener {
            val visible = Rect()
            content.getWindowVisibleDisplayFrame(visible)
            val covered = content.rootView.height - visible.height()
            val up = covered > content.rootView.height * 0.15
            if (up != keyboardIsUp) {
                keyboardIsUp = up
                applyVisibility()
            }
        }
    }
}
