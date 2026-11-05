package com.tele.k_guard.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.tele.k_guard.MainActivity
import com.tele.k_guard.R
import com.tele.k_guard.data.repository.FamilyRepository
import com.tele.k_guard.receiver.KGuardDeviceAdminReceiver
import com.tele.k_guard.ui.screens.DeviceLockActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Receives safety-alert pushes for parents, executes remote child device commands, and keeps FCM tokens registered. */
class KGuardMessagingService : FirebaseMessagingService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        scope.launch { runCatching { FamilyRepository.registerPushToken(token) } }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val action = data["action"]

        if (!action.isNullOrEmpty()) {
            handleRemoteCommand(action, data)
            return
        }

        val title = message.notification?.title ?: "K-Guard safety alert"
        val body = message.notification?.body ?: return
        showAlert(this, title, body)
    }

    private fun handleRemoteCommand(action: String, data: Map<String, String>) {
        when (action) {
            "SWITCH_OFF", "LOCK_SCREEN" -> {
                val reason = data["reason"] ?: "Parent Remote Screen Lock"
                val childName = data["childName"] ?: "Child"

                // 1. Instantly turn off screen and lock phone if Device Admin is active
                val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
                val adminComponent = ComponentName(this, KGuardDeviceAdminReceiver::class.java)
                if (dpm != null && dpm.isAdminActive(adminComponent)) {
                    runCatching { dpm.lockNow() }
                }

                // 2. Launch fullscreen Lock screen overlay
                val lockIntent = DeviceLockActivity.createIntent(this, childName, reason)
                startActivity(lockIntent)
            }
            "SOUND_ALARM" -> {
                playAudibleAlertChime()
            }
        }
    }

    private fun playAudibleAlertChime() {
        runCatching {
            val alertUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            val ringtone = RingtoneManager.getRingtone(applicationContext, alertUri)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                ringtone.audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            }
            ringtone.play()
        }
    }

    companion object {
        const val CHANNEL_ID = "safety_alerts"

        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val channel = NotificationChannel(
                CHANNEL_ID, "Safety alerts", NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Alerts when K-Guard flags content for one of your children." }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        fun showAlert(context: Context, title: String, body: String) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) return
            ensureChannel(context)
            val open = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val n = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(open)
                .build()
            NotificationManagerCompat.from(context).notify(System.currentTimeMillis().toInt(), n)
        }
    }
}
