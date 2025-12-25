package com.rsinkwitz.notif_snd_assist

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class NotificationMonitorService : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        startForegroundServiceCompat()
    }

    private fun startForegroundServiceCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = "notif_snd_assist_service"
            val channelName = "NotificationSoundAssistant Service"
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(channelId) == null) {
                val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW)
                nm.createNotificationChannel(channel)
            }
            val notification = Notification.Builder(this, channelId)
                .setContentTitle("NotificationSoundAssistant läuft")
                .setContentText("Benachrichtigungen werden überwacht")
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
                .build()
            startForeground(1, notification)
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        Log.d("Piepton", "Notification from ${sbn.packageName}")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = sbn.notification.channelId
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = nm.getNotificationChannel(channelId)
            if (channel != null) {
                val soundUri = channel.sound
                if (soundUri == null || Settings.System.DEFAULT_NOTIFICATION_URI == soundUri) {
                    // Default-Sound erkannt, öffne Einstellungen
                    val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, sbn.packageName)
                        putExtra(Settings.EXTRA_CHANNEL_ID, channelId)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(intent)
                }
            }
        } else {
            // Für ältere Android-Versionen: App-Benachrichtigungseinstellungen öffnen
            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, sbn.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        }
    }
}
