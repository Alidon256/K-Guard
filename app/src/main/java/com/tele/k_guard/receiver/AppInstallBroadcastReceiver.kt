package com.tele.k_guard.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.tele.k_guard.data.repository.FamilyRepository
import com.tele.k_guard.data.repository.ParentalControlsRepository
import com.tele.k_guard.data.repository.UserPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

/**
 * Listens for package installations on the device.
 * When an application is installed, automatically files an approval request to K-Guard backend
 * and notifies parents in real-time.
 */
class AppInstallBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null || intent.action != Intent.ACTION_PACKAGE_ADDED) return

        // PACKAGE_ADDED also fires for app updates; only process fresh installs.
        if (intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return

        val uri = intent.data ?: return
        val packageName = uri.schemeSpecificPart ?: return

        // Ignore our own package
        if (packageName == context.packageName) return

        // Filing approval requests needs a signed-in family; skip silently otherwise.
        if (FamilyRepository.currentUser == null) {
            Log.i("KGuardAppInstall", "Skipping package install for $packageName: no signed-in user")
            return
        }

        // Keep the broadcast alive until the async work completes.
        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val prefs = UserPreferencesRepository(context)
                val activeChildId = prefs.activeChildIdFlow.firstOrNull()

                val pm = context.packageManager
                val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getApplicationInfo(packageName, 0)
                }

                val appName = pm.getApplicationLabel(appInfo).toString()
                val category = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    when (appInfo.category) {
                        ApplicationInfo.CATEGORY_GAME -> "Gaming"
                        ApplicationInfo.CATEGORY_SOCIAL -> "Social Media"
                        ApplicationInfo.CATEGORY_NEWS -> "News & Media"
                        ApplicationInfo.CATEGORY_VIDEO -> "Video & Entertainment"
                        ApplicationInfo.CATEGORY_AUDIO -> "Music & Audio"
                        ApplicationInfo.CATEGORY_PRODUCTIVITY -> "Productivity"
                        else -> "General"
                    }
                } else {
                    "General"
                }

                val familyId = try {
                    FamilyRepository.ensureFamily()
                } catch (_: Exception) {
                    null
                }

                if (familyId != null && activeChildId != null) {
                    ParentalControlsRepository.requestAppApproval(
                        familyId = familyId,
                        childId = activeChildId,
                        appName = appName,
                        packageName = packageName,
                        category = category,
                        minAgeRating = if (category == "Gaming") 10 else if (category == "Social Media") 13 else 3
                    )
                    Log.i("KGuardAppInstall", "Approval requested for installed app: $appName ($packageName)")
                }
            } catch (e: Exception) {
                Log.e("KGuardAppInstall", "Error processing package install: $packageName", e)
            } finally {
                result.finish()
            }
        }
    }
}
