package com.tele.k_guard.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.tele.k_guard.MainActivity
import com.tele.k_guard.R
import com.tele.k_guard.data.model.FamilyPrivacy
import com.tele.k_guard.data.model.ModerationDecision
import com.tele.k_guard.data.model.QuietHours
import com.tele.k_guard.data.repository.FamilyRepository
import com.tele.k_guard.ui.screens.DeviceLockActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.SimpleTimeZone
import java.util.concurrent.atomic.AtomicInteger

/** Receives safety-alert & app-approval pushes for parents and keeps the device's FCM token registered. */
class KGuardMessagingService : FirebaseMessagingService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        scope.launch { runCatching { FamilyRepository.registerPushToken(token) } }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data

        if (data["type"] == "remote_lock") {
            handleRemoteLock(this, data)
            return
        }

        if (data["type"] == "remote_command") {
            handleRemoteCommand(this, data)
            return
        }

        val title = message.notification?.title ?: data["title"] ?: "K-Guard alert"
        val rawBody = message.notification?.body ?: data["body"] ?: return
        val familyId = data["familyId"]

        if (familyId == null) {
            Log.w(TAG, "No familyId in push payload; showing alert without privacy filtering")
            showAlert(this, title, rawBody, destinationRoute(data["type"]))
            return
        }

        scope.launch {
            val privacy = runCatching { FamilyRepository.privacy(familyId).first() }.getOrNull()
            if (privacy == null) {
                Log.w(TAG, "Could not load privacy settings; showing alert (fail-open)")
                showAlert(this@KGuardMessagingService, title, rawBody, destinationRoute(data["type"]))
                return@launch
            }

            if (!passesPrivacyFilter(privacy, data["decision"])) return@launch

            // includeSnippet gates whether flagged message content is displayed on the device.
            val snippet = data["snippet"]
            val body = if (snippet != null && !privacy.includeSnippet) {
                "New safety alert — open K-Guard for details."
            } else {
                rawBody
            }

            showAlert(this@KGuardMessagingService, title, body, destinationRoute(data["type"]))
        }
    }

    private fun destinationRoute(type: String?): String =
        if (type == "app_approval" || type == "app_approval_response") "app_management" else "review_queue"

    /**
     * BLOCK decisions always show. Everything else obeys notifyOn, and quiet hours
     * suppress all non-block alerts during the window.
     */
    private fun passesPrivacyFilter(privacy: FamilyPrivacy, decision: String?): Boolean {
        val level = ModerationDecision.fromString(decision)
        if (level == ModerationDecision.BLOCK) return true

        val allowed = when (privacy.notifyOn) {
            FamilyPrivacy.NOTIFY_ALL -> true
            FamilyPrivacy.NOTIFY_WARN_AND_ABOVE -> level != ModerationDecision.ALLOW
            FamilyPrivacy.NOTIFY_BLOCK_ONLY -> false
            else -> level != ModerationDecision.ALLOW
        }
        if (!allowed) return false
        if (privacy.quietHours.enabled && inQuietHours(System.currentTimeMillis(), privacy.quietHours)) {
            return false
        }
        return true
    }

    /** Parent-local quiet-hours check; the window may cross midnight (e.g. 21:00 → 07:00). */
    private fun inQuietHours(nowUtcMillis: Long, q: QuietHours): Boolean {
        val start = parseHourMinutes(q.start) ?: return false
        val end = parseHourMinutes(q.end) ?: return false
        val cal = Calendar.getInstance(SimpleTimeZone(q.utcOffsetMinutes * 60_000, "UTC"))
        cal.timeInMillis = nowUtcMillis
        val minutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        return if (start <= end) minutes in start until end else minutes >= start || minutes < end
    }

    private fun parseHourMinutes(value: String): Int? {
        val parts = value.split(":")
        if (parts.size != 2) return null
        val h = parts[0].trim().toIntOrNull() ?: return null
        val m = parts[1].trim().toIntOrNull() ?: return null
        if (h !in 0..23 || m !in 0..59) return null
        return h * 60 + m
    }

    /** Data-only push asking the child device to bring up the remote lock screen. */
    private fun handleRemoteLock(context: Context, data: Map<String, String>) {
        val childName = data["childName"] ?: "Child"
        val reason = data["reason"] ?: "Parent Remote Screen Lock"
        val emergencyNumber = data["emergencyNumber"] ?: "999"
        context.startActivity(DeviceLockActivity.createIntent(context, childName, reason, emergencyNumber))
        // The lock screen may start behind the keyguard or be dismissed; raise a heads-up alert too.
        showAlert(context, "K-Guard Remote Lock", "Screen switched off by parent for $childName: $reason")
    }

    /**
     * Any other remote command from the parent. Unlock closes the lock screen; alarm rings the
     * device. Limit/schedule commands need no local action here — the parent's Firestore write
     * to `devices/{childId}` is the source of truth the UI reads.
     */
    private fun handleRemoteCommand(context: Context, data: Map<String, String>) {
        when (data["action"]) {
            "unlock_device" -> {
                // Explicit broadcast to the (possibly open) lock screen; it finishes itself on receive.
                context.sendBroadcast(
                    Intent(ACTION_UNLOCK_DEVICE).setPackage(context.packageName)
                )
            }
            "sound_alarm" -> scope.launch { runCatching { playAlarm(context) } }
        }
    }

    /** Rings the alarm tone loudly for ~15 seconds, then releases. */
    private suspend fun playAlarm(context: Context) {
        val uri = android.media.RingtoneManager.getActualDefaultRingtoneUri(
            context, android.media.RingtoneManager.TYPE_ALARM
        ) ?: return
        val player = android.media.MediaPlayer()
        player.setDataSource(context, uri)
        player.setAudioAttributes(
            android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        player.isLooping = true
        player.prepare()
        val am = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        val previous = am.getStreamVolume(android.media.AudioManager.STREAM_ALARM)
        am.setStreamVolume(
            android.media.AudioManager.STREAM_ALARM,
            am.getStreamMaxVolume(android.media.AudioManager.STREAM_ALARM), 0
        )
        player.start()
        kotlinx.coroutines.delay(15_000)
        player.stop()
        player.release()
        am.setStreamVolume(android.media.AudioManager.STREAM_ALARM, previous, 0)
    }

    companion object {
        private const val TAG = "KGuardMessaging"
        const val CHANNEL_ID = "safety_alerts"
        /** Broadcast that tells [DeviceLockActivity] a parent has unlocked the device. */
        const val ACTION_UNLOCK_DEVICE = "com.tele.k_guard.remote.UNLOCK_DEVICE"
        private val notificationId = AtomicInteger(0)

        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val channel = NotificationChannel(
                CHANNEL_ID, "Safety & Approval Alerts", NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Alerts when K-Guard flags content or receives child app install requests." }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        fun showAlert(context: Context, title: String, body: String, destinationRoute: String? = null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) return
            ensureChannel(context)
            val id = notificationId.incrementAndGet()
            val intent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                if (destinationRoute != null) {
                    putExtra("navigate_to", destinationRoute)
                }
            }
            val open = PendingIntent.getActivity(
                context, id, intent,
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
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .build()
            NotificationManagerCompat.from(context).notify(id, n)
        }
    }
}
