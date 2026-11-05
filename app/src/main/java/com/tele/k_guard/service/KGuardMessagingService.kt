package com.tele.k_guard.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.tele.k_guard.MainActivity
import com.tele.k_guard.R
import com.tele.k_guard.data.repository.FamilyRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Receives safety-alert & app-approval pushes for parents and keeps the device's FCM token registered. */
class KGuardMessagingService : FirebaseMessagingService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        scope.launch { runCatching { FamilyRepository.registerPushToken(token) } }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title ?: message.data["title"] ?: "K-Guard alert"
        val body = message.notification?.body ?: message.data["body"] ?: return
        val type = message.data["type"]
        val destinationRoute = if (type == "app_approval" || type == "app_approval_response") {
            "app_management"
        } else {
            "review_queue"
        }
        showAlert(this, title, body, destinationRoute)
    }

    companion object {
        const val CHANNEL_ID = "safety_alerts"

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
            val intent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                if (destinationRoute != null) {
                    putExtra("navigate_to", destinationRoute)
                }
            }
            val open = PendingIntent.getActivity(
                context, System.currentTimeMillis().toInt(), intent,
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
