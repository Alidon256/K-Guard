package com.tele.k_guard.vpn

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/**
 * Restarts web protection after a reboot when the parent left it on, so filtering does not silently
 * disappear overnight. Requires RECEIVE_BOOT_COMPLETED and a manifest receiver entry for
 * ACTION_BOOT_COMPLETED (exported="false" is correct: the system delivers protected broadcasts to
 * non-exported manifest receivers, and no third-party app may trigger this one).
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (VpnConfigStore(context).load().enabled) {
            ContextCompat.startForegroundService(context, Intent(context, KGuardVpnService::class.java))
        }
    }
}
