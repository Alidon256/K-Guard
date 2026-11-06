package com.tele.k_guard.screentime

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.telecom.TelecomManager
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.text.DateFormat
import java.util.Date

/**
 * Full-screen window drawn above every app on the child's phone. This is how a lock is enforced:
 * Android no longer lets a background service open an activity, but an app with "Display over other apps"
 * may show an overlay window at any time. It swallows touches and the back key; Home and Recents cannot open
 * anything underneath because the overlay stays on top of whatever is shown.
 *
 * An emergency call button is always available. While the phone app is in front, the overlay steps aside.
 */
class LockOverlay(private val context: Context) {
    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var root: View? = null
    private var title: TextView? = null
    private var body: TextView? = null
    private var until: TextView? = null
    private var action: Button? = null
    private var shownKey: String? = null

    /** Package of the phone app; while it is in front, the lock steps aside so the child can call. */
    val dialerPackage: String? by lazy {
        (context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager)?.defaultDialerPackage
    }

    val isShowing: Boolean get() = root != null

    fun canDraw(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)

    enum class Mode { PHONE_LOCK, APP_BLOCK }

    /** Shows or updates the overlay. Returns false if Android does not allow it (permission missing). */
    fun show(mode: Mode, heading: String, message: String, untilMillis: Long?, childName: String?): Boolean {
        if (!canDraw()) return false
        val key = "$mode|$heading|$message|$untilMillis"
        if (root != null && key == shownKey) return true
        return try {
            if (root == null) attach()
            title?.text = heading
            body?.text = message
            until?.text = when {
                mode == Mode.APP_BLOCK -> childName?.let { "K-Guard · $it" } ?: "K-Guard"
                untilMillis != null -> "Unlocks at " + DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(untilMillis))
                else -> "Ask your parent to unlock it."
            }
            action?.text = if (mode == Mode.PHONE_LOCK) "Emergency call" else "OK"
            action?.setOnClickListener { if (mode == Mode.PHONE_LOCK) openDialer() else { goHome(); hide() } }
            shownKey = key
            true
        } catch (e: Exception) {
            Log.e(TAG, "Could not show lock overlay", e)
            hide()
            false
        }
    }

    fun hide() {
        root?.let { runCatching { wm.removeView(it) } }
        root = null; title = null; body = null; until = null; action = null; shownKey = null
    }

    fun goHome() {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(home) }
    }

    private fun openDialer() {
        hide() // the service keeps the lock off only while the phone app is in front
        val dial = Intent(Intent.ACTION_DIAL, Uri.parse("tel:")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(dial) }
    }

    @SuppressLint("SetTextI18n")
    private fun attach() {
        fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), context.resources.displayMetrics).toInt()

        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(32), dp(32), dp(32), dp(32))
        }
        val shield = TextView(context).apply { text = "🛡️"; setTextSize(TypedValue.COMPLEX_UNIT_SP, 56f); gravity = Gravity.CENTER }
        title = TextView(context).apply {
            setTextColor(Color.WHITE); setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f); gravity = Gravity.CENTER
            setTypeface(typeface, android.graphics.Typeface.BOLD); setPadding(0, dp(16), 0, dp(8))
        }
        body = TextView(context).apply { setTextColor(0xFFCFD8E3.toInt()); setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f); gravity = Gravity.CENTER }
        until = TextView(context).apply { setTextColor(0xFF90CAF9.toInt()); setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f); gravity = Gravity.CENTER; setPadding(0, dp(16), 0, dp(32)) }
        action = Button(context).apply { isAllCaps = false }
        listOf(shield, title, body, until, action).forEach { column.addView(it) }

        // Swallow the back key and every touch outside our button.
        val frame = object : FrameLayout(context) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean =
                if (event.keyCode == KeyEvent.KEYCODE_BACK) true else super.dispatchKeyEvent(event)
        }.apply {
            setBackgroundColor(0xF20B1220.toInt())
            isClickable = true
            isFocusable = true
            isFocusableInTouchMode = true
            addView(column, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        }

        @Suppress("DEPRECATION")
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else WindowManager.LayoutParams.TYPE_PHONE
        @Suppress("DEPRECATION")
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.CENTER }
        wm.addView(frame, params)
        frame.requestFocus()
        root = frame
    }

    private companion object { const val TAG = "KGuardLock" }
}
