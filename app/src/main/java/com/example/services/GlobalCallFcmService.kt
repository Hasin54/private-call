package com.example.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Custom push notifications receiver using Firebase Cloud Messaging (FCM).
 * Manages high-priority audio/video ring alerts with system notification trays.
 */
class GlobalCallFcmService : FirebaseMessagingService() {

    private val TAG = "GlobalCallFcmService"
    private val CHANNEL_ID = "global_call_alerts_channel"

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New secure FCM token generated: $token")
        // In a wider production system, send this token to Firestore to target custom user devices.
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "Notification packet arrived from: ${remoteMessage.from}")

        // Check if message contains data payload
        if (remoteMessage.data.isNotEmpty()) {
            val title = remoteMessage.data["title"] ?: "Incoming Call"
            val body = remoteMessage.data["body"] ?: "A friend is calling you on Global Call..."
            showNotification(title, body)
        }

        // Check if message contains notification payload
        remoteMessage.notification?.let {
            showNotification(it.title ?: "Calling Notice", it.body ?: "Connecting call session...")
        }
    }

    private fun showNotification(title: String, messageBody: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create high importance channel for calling ringtones
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Calling and Chat Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Receives live video/audio calls and direct chat message notifications."
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentTitle(title)
            .setContentText(messageBody)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)

        notificationManager.notify(System.currentTimeMillis().toInt(), notificationBuilder.build())
    }
}
