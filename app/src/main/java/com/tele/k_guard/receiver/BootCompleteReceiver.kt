package com.tele.k_guard.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.tele.k_guard.service.KGuardCompanionService

/**
 * Ensures K-Guard companion service restarts immediately after device reboot,
 * preventing children from bypassing Bedtime or Remote Screen Lock by power-cycling the device.
 */
class BootCompleteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED || action == "android.intent.action.QUICKBOOT_POWERON" || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            Log.i(TAG, "Device boot completed ($action). Restarting KGuardCompanionService.")
            try {
                KGuardCompanionService.startCompanionService(context)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start KGuardCompanionService on boot", e)
            }
        }
    }

    companion object {
        private const val TAG = "BootCompleteReceiver"
    }
}
