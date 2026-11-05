package com.tele.k_guard.receiver

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

/**
 * Device Administrator receiver that enables K-Guard to remotely turn off the screen
 * and lock the child's device using DevicePolicyManager.lockNow().
 */
class KGuardDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Toast.makeText(context, "K-Guard Child Protection Administrator Enabled", Toast.LENGTH_SHORT).show()
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Toast.makeText(context, "K-Guard Child Protection Administrator Disabled", Toast.LENGTH_SHORT).show()
    }
}

