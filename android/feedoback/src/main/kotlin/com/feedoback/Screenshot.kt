package com.feedoback

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import java.io.ByteArrayOutputStream
import java.util.Collections
import java.util.WeakHashMap

/**
 * A picture of the screen the visitor is complaining about.
 *
 * The strongest guarantee here costs nothing: the visitor always sees the
 * picture before it goes, and can take it off. Nothing is sent that the person
 * looking at it did not look at first.
 */
object Screenshot {
    /** Quality that keeps text legible without sending a photograph's worth of
     *  bytes over someone's mobile data. */
    const val QUALITY = 80
    const val CONTENT_TYPE = "image/jpeg"

    /** Held weakly, so marking a row in a list that is later recycled does not
     *  keep it alive. */
    private val redacted: MutableSet<View> =
        Collections.newSetFromMap(WeakHashMap<View, Boolean>())

    /**
     * Regions the host app marked, in the root view's own pixels.
     *
     * A view is the right thing to mark when there is one. Flutter draws its
     * entire interface into a single view, so there is no view to hand over
     * and a rectangle is the only thing that can be said; its SDK keeps these
     * in step with where its widgets are. The same applies to anything else
     * drawn rather than laid out — a canvas, a game, a chart.
     */
    @Volatile
    private var regions: List<Rect> = emptyList()

    /**
     * Marks a view whose contents must never leave the device. Password fields
     * need no marking: they are found on their own, because forgetting one of
     * those is the expensive mistake.
     */
    fun redact(view: View) {
        redacted.add(view)
    }

    fun unredact(view: View) {
        redacted.remove(view)
    }

    /**
     * Replaces the marked regions rather than adding to them: the caller knows
     * where all of them are, and a set that could only grow would keep
     * painting over a place nothing sensitive has been for ten screens.
     */
    fun setRedactedRegions(regions: List<Rect>) {
        this.regions = regions.filter { it.width() > 0 && it.height() > 0 }
    }

    /**
     * Captures what the visitor is looking at.
     *
     * The view tree is drawn into a canvas we own, so anything sensitive is
     * painted over as it is drawn and the real pixels never exist in a bitmap
     * at all.
     *
     * A SurfaceView is the exception, and Flutter is one — so is every video
     * player, map and camera preview. The system composites those on its own
     * layer, `View.draw` leaves the hole they punched exactly as transparent
     * as it found it, and the only way to read one back is PixelCopy, which
     * hands over what is already on screen. Those are filled in underneath
     * what was drawn, so a button over a video still comes out over the video,
     * and the redaction goes on last either way.
     *
     * Asynchronous because PixelCopy is. Neither path ever lets the host app
     * see a failure.
     */
    fun capture(
        activity: Activity?,
        hiding: List<View> = emptyList(),
        onResult: (ByteArray?) -> Unit,
    ) {
        val root = activity?.window?.decorView?.rootView
        if (root == null || root.width <= 0 || root.height <= 0) return onResult(null)

        // An app that set FLAG_SECURE means it, and a feedback SDK is not an
        // exception to it.
        val secure = activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE
        if (secure != 0) return onResult(null)

        val hidden = hiding.filter { it.visibility == View.VISIBLE }
        hidden.forEach { it.visibility = View.INVISIBLE }

        val finish = { bytes: ByteArray? ->
            hidden.forEach { it.visibility = View.VISIBLE }
            onResult(bytes)
        }

        val bitmap = try {
            Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888).also {
                root.draw(Canvas(it))
            }
        } catch (error: Throwable) {
            // A capture that fails must never take the host app with it.
            return finish(null)
        }

        val surfaces = surfaceViews(root)
        if (surfaces.isEmpty()) {
            paintOver(bitmap, root)
            return finish(encode(bitmap))
        }

        fillSurfaces(surfaces, bitmap, root) {
            paintOver(bitmap, root)
            finish(encode(bitmap))
        }
    }

    /**
     * Copies each composited layer into the hole it left.
     *
     * DST_OVER paints only where the bitmap is still transparent, which is
     * exactly the hole and nothing else — so whatever the view tree drew above
     * a surface survives, without having to work out what that was.
     */
    private fun fillSurfaces(
        surfaces: List<SurfaceView>,
        bitmap: Bitmap,
        root: View,
        onDone: () -> Unit,
    ) {
        val canvas = Canvas(bitmap)
        val under = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OVER) }
        val handler = Handler(Looper.getMainLooper())
        var outstanding = surfaces.size

        val one = { copied: Bitmap?, at: Rect ->
            if (copied != null) {
                canvas.drawBitmap(copied, at.left.toFloat(), at.top.toFloat(), under)
                copied.recycle()
            }
            outstanding -= 1
            if (outstanding == 0) onDone()
        }

        for (surface in surfaces) {
            val at = boundsIn(surface, root)
            val copy = try {
                Bitmap.createBitmap(surface.width, surface.height, Bitmap.Config.ARGB_8888)
            } catch (error: Throwable) {
                one(null, at)
                continue
            }

            try {
                PixelCopy.request(surface, copy, { status ->
                    one(if (status == PixelCopy.SUCCESS) copy else null.also { copy.recycle() }, at)
                }, handler)
            } catch (error: Throwable) {
                // A surface with nothing behind it yet, or one the system will
                // not hand over. The hole stays where it was.
                copy.recycle()
                one(null, at)
            }
        }
    }

    /** Last, and over everything: a block drawn under a video is not a block. */
    private fun paintOver(bitmap: Bitmap, root: View) {
        val canvas = Canvas(bitmap)
        val paint = Paint().apply { color = Color.rgb(0x63, 0x63, 0x67) }
        sensitiveViews(root).forEach { canvas.drawRect(boundsIn(it, root), paint) }
        regions.forEach { canvas.drawRect(it, paint) }
    }

    private fun encode(bitmap: Bitmap): ByteArray =
        ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)
            bitmap.recycle()
            out.toByteArray()
        }

    /** Every layer the system composites rather than draws. */
    internal fun surfaceViews(view: View): List<SurfaceView> {
        val found = mutableListOf<SurfaceView>()

        fun walk(current: View) {
            if (current.visibility != View.VISIBLE) return
            if (current is SurfaceView) {
                found.add(current)
                return
            }
            if (current is ViewGroup) {
                for (index in 0 until current.childCount) walk(current.getChildAt(index))
            }
        }

        walk(view)
        return found
    }

    /** Anything the host app marked, plus every password field, which is the
     *  one nobody should have to remember. */
    internal fun sensitiveViews(root: View): List<View> {
        val found = mutableListOf<View>()

        fun walk(view: View) {
            if (view.visibility != View.VISIBLE) return
            if (redacted.contains(view) || isPassword(view)) {
                found.add(view)
                // No need to walk into something already painted over.
                return
            }
            if (view is ViewGroup) {
                for (index in 0 until view.childCount) walk(view.getChildAt(index))
            }
        }

        walk(root)
        return found
    }

    internal fun isPassword(view: View): Boolean {
        if (view !is EditText) return false
        val variation = view.inputType and InputType.TYPE_MASK_VARIATION
        return variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
            variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
    }

    private fun boundsIn(view: View, root: View): Rect {
        val location = IntArray(2)
        val rootLocation = IntArray(2)
        view.getLocationInWindow(location)
        root.getLocationInWindow(rootLocation)
        val left = location[0] - rootLocation[0]
        val top = location[1] - rootLocation[1]
        return Rect(left, top, left + view.width, top + view.height)
    }
}
