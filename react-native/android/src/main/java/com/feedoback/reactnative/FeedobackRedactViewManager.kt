package com.feedoback.reactnative

import android.view.ViewGroup
import com.facebook.react.module.annotations.ReactModule
import com.facebook.react.uimanager.ThemedReactContext
import com.facebook.react.uimanager.ViewGroupManager
import com.facebook.react.uimanager.ViewManagerDelegate
import com.facebook.react.viewmanagers.FeedobackRedactViewManagerDelegate
import com.facebook.react.viewmanagers.FeedobackRedactViewManagerInterface
import com.facebook.react.views.view.ReactViewGroup
import com.feedoback.Feedoback

/**
 * `<FeedobackRedact>`, as a view.
 *
 * A real view rather than a tag passed to the module, because the screenshot
 * pass walks the Activity's view tree and paints over what it finds marked
 * there. Handing it the view once, when React creates it, means the mark
 * belongs to the thing on screen rather than to a node id the reconciler is
 * free to reuse.
 *
 * The SDK holds marked views weakly, so a row in a recycled list does not keep
 * one alive after React has taken it away.
 */
@ReactModule(name = FeedobackRedactViewManager.NAME)
class FeedobackRedactViewManager :
    // A ViewGroupManager and not a SimpleViewManager: this view wraps whatever
    // it was given, and Fabric asks a manager for the IViewGroupManager half
    // before it will mount a single child into it.
    ViewGroupManager<ViewGroup>(),
    FeedobackRedactViewManagerInterface<ViewGroup> {

    private val delegate = FeedobackRedactViewManagerDelegate(this)

    override fun getDelegate(): ViewManagerDelegate<ViewGroup> = delegate

    override fun getName(): String = NAME

    /** React's own view group, so it lays out, clips and draws exactly as the
     *  `<View>` it stands in for. */
    override fun createViewInstance(context: ThemedReactContext): ViewGroup = ReactViewGroup(context)

    override fun setRedacted(view: ViewGroup?, value: Boolean) {
        val target = view ?: return
        if (value) Feedoback.redact(target) else Feedoback.unredact(target)
    }

    companion object {
        const val NAME = "FeedobackRedactView"
    }
}
