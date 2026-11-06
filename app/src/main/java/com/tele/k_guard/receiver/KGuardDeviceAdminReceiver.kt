package com.tele.k_guard.receiver

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast

/**
 * Device Administrator receiver that enables K-Guard to remotely turn off the screen
 * and lock the child's device using DevicePolicyManager.lockNow().
 */
class KGuardDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.d(TAG, "K-Guard Device Admin enabled")
        Toast.makeText(context, "K-Guard device admin is on", Toast.LENGTH_SHORT).show()
        com.tele.k_guard.service.KGuardCompanionService.requestSync(context)
    }

    /** Shown by Android when someone tries to switch device admin off. */
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence =
        "Turning this off weakens screen time protection and lets K-Guard be removed. Your parent will be told."

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.w(TAG, "K-Guard Device Admin disabled")
        // Report straight away; the server tells the parent (see onDeviceProtectionChanged).
        com.tele.k_guard.service.KGuardCompanionService.requestSync(context)
    }

    companion object {
        const val TAG = "KGuardDeviceAdmin"

        fun getComponentName(context: Context): ComponentName {
            return ComponentName(context.applicationContext, KGuardDeviceAdminReceiver::class.java)
        }

        fun isAdminActive(context: Context): Boolean {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager ?: return false
            return dpm.isAdminActive(getComponentName(context))
        }

        fun lockScreenNow(context: Context): Boolean {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager ?: return false
            return if (isAdminActive(context)) {
                try {
                    Log.i(TAG, "Executing DevicePolicyManager.lockNow() to switch off & lock screen")
                    dpm.lockNow()
                    true
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to execute lockNow()", e)
                    false
                }
            } else {
                Log.w(TAG, "Device Admin is not active; cannot invoke lockNow()")
                false
            }
        }

        /**
         * Android's "Activate device admin" screen refuses to open as a new task ("Cannot start
         * ADD_DEVICE_ADMIN as a new task") and closes at once, so this intent has no NEW_TASK flag:
         * start it from an Activity, ideally with an activity-result launcher.
         */
        fun createAdminRequestIntent(context: Context): Intent =
            Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, getComponentName(context))
                putExtra(
                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "Lets K-Guard switch the screen off when your parent locks the phone or at bedtime. " +
                        "If it is turned off, your parent is told."
                )
            }
    }
}


