package com.tele.k_guard

import android.content.Context
import android.content.pm.ApplicationInfo

/**
 * Debug-only demo mode for previews and demos without a backend. Inert in release builds.
 * Start with: adb shell am start -n com.tele.k_guard/.MainActivity --es demo_route home
 */
object DebugPreview {
    @Volatile var demo = false
    @Volatile var startRoute: String? = null
    @Volatile var webTab = 0

    fun isDebuggable(context: Context) =
        (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    fun init(context: Context, route: String?, tab: Int) {
        if (!isDebuggable(context)) return
        if (route != null) { demo = true; startRoute = route }
        webTab = tab
    }
}
