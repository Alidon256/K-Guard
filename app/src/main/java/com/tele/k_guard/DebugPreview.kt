package com.tele.k_guard

import android.content.Context
import android.content.pm.ApplicationInfo

/**
 * Debug-only demo switch.
 *
 * STUB: the original DebugPreview was never committed, so master did not compile. This keeps every
 * existing call site working. Replace it with the teammate's real file when it is pushed.
 * - [demo] lets the app run on canned data without signing in.
 * - [startRoute] and [webTab] let a debug launch open a given screen: pass the `demo_route` and `demo_tab` intent extras.
 */
object DebugPreview {
    @JvmField var demo: Boolean = false
    @JvmField var startRoute: String? = null
    @JvmField var webTab: Int = 0

    fun isDebuggable(context: Context): Boolean =
        (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    fun init(context: Context, route: String?, tab: Int) {
        if (route == null || !isDebuggable(context)) return
        startRoute = route
        webTab = tab
        demo = true
    }
}
