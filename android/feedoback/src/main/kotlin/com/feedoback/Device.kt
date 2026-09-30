package com.feedoback

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import java.util.TimeZone

/**
 * What the SDK can read about where the visitor is, and nothing more.
 *
 * Every value here is either something the app declares about itself or
 * something the OS will tell any app. No advertising id, no ANDROID_ID, no
 * fingerprint: the only identifier this SDK mints is the random install id in
 * the store, which dies with the app.
 */
object Device {
    /** Read from the OS rather than typed into a config, so a developer cannot
     *  get it wrong and wonder why nothing arrives. */
    fun bundleId(context: Context): String = context.packageName

    fun appVersion(context: Context): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
    } catch (error: Exception) {
        ""
    }

    fun buildNumber(context: Context): String = try {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode.toString()
        else @Suppress("DEPRECATION") info.versionCode.toString()
    } catch (error: Exception) {
        ""
    }

    /** "Pixel 9" rather than a codename: unlike iOS, Android's marketing name
     *  is the one the OS reports, so it needs no lookup table. */
    fun model(): String = listOf(Build.MANUFACTURER, Build.MODEL)
        .filter { it.isNotBlank() }
        .joinToString(" ")
        .let { if (it.startsWith(Build.MODEL)) Build.MODEL else it }
        .ifEmpty { Build.MODEL }

    fun osVersion(): String = Build.VERSION.RELEASE ?: Build.VERSION.SDK_INT.toString()

    fun locale(context: Context): String {
        val locales = context.resources.configuration.locales
        val locale = if (locales.isEmpty) java.util.Locale.getDefault() else locales[0]
        return locale.toLanguageTag()
    }

    fun timezone(): String = TimeZone.getDefault().id

    /**
     * Density-independent pixels, not physical ones: the same units a
     * developer lays out in, and the same units iOS reports.
     */
    fun viewport(context: Context): Viewport {
        val metrics = context.resources.displayMetrics
        return Viewport(
            width = (metrics.widthPixels / metrics.density).toInt(),
            height = (metrics.heightPixels / metrics.density).toInt(),
        )
    }

    fun scale(context: Context): Double = context.resources.displayMetrics.density.toDouble()

    fun orientation(context: Context): Orientation =
        if (context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            Orientation.LANDSCAPE
        } else {
            Orientation.PORTRAIT
        }

    /**
     * Whether they were online, and roughly how. Answers null rather than
     * guessing when the host app has not granted ACCESS_NETWORK_STATE, which
     * this SDK does not ask for.
     */
    fun network(context: Context): Network? = try {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork)
        when {
            capabilities == null -> Network.NONE
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> Network.WIFI
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> Network.CELLULAR
            else -> null
        }
    } catch (error: Exception) {
        null
    }

    /** Everything the server's app context asks for. */
    fun screenContext(context: Context, route: String, title: String): ScreenContext =
        ScreenContext(
            route = route,
            title = title.ifEmpty { route },
            bundleId = bundleId(context),
            appVersion = appVersion(context),
            buildNumber = buildNumber(context),
            osVersion = osVersion(),
            deviceModel = model(),
            locale = locale(context),
            timezone = timezone(),
            viewport = viewport(context),
            scale = scale(context),
            orientation = orientation(context),
            network = network(context),
        )
}
