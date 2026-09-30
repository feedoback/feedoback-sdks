package com.feedoback.reactnative

import com.facebook.react.BaseReactPackage
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.module.model.ReactModuleInfo
import com.facebook.react.module.model.ReactModuleInfoProvider
import com.facebook.react.uimanager.ViewManager

/** Autolinked: nothing in a host app has to name either of these. */
class FeedobackPackage : BaseReactPackage() {
    override fun getModule(name: String, context: ReactApplicationContext): NativeModule? =
        if (name == FeedobackModule.NAME) FeedobackModule(context) else null

    override fun getReactModuleInfoProvider() = ReactModuleInfoProvider {
        mapOf(
            FeedobackModule.NAME to ReactModuleInfo(
                name = FeedobackModule.NAME,
                className = FeedobackModule.NAME,
                canOverrideExistingModule = false,
                needsEagerInit = false,
                isCxxModule = false,
                isTurboModule = true,
            ),
        )
    }

    override fun createViewManagers(
        context: ReactApplicationContext,
    ): List<ViewManager<*, *>> = listOf(FeedobackRedactViewManager())
}
