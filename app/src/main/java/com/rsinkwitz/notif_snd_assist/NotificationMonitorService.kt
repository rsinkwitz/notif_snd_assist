package com.rsinkwitz.notif_snd_assist

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import android.widget.Toast

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

        val prefs = getSharedPreferences("notif_snd_assist_prefs", Context.MODE_PRIVATE)
        val seenApps = prefs.getStringSet("seen_apps", mutableSetOf()) ?: mutableSetOf()
        val lastAppKey = "last_dialog_app"
        val packageName = sbn.packageName
        val pendingKey = "pending_apps"
        val pendingApps = prefs.getStringSet(pendingKey, mutableSetOf()) ?: mutableSetOf()

        if (!seenApps.contains(packageName) && !pendingApps.contains(packageName)) {
            // App wurde noch nicht behandelt und ist nicht in pending
            val newPending = pendingApps.toMutableSet()
            newPending.add(packageName)
            prefs.edit().putStringSet(pendingKey, newPending).apply()
            Log.i("Piepton", "Neue App in pending-Liste: $packageName")
            // Kein automatisches Öffnen mehr!
            return
        }
        // ...bisherige Logik für eigene App kann hier noch ergänzt werden...
    }
}
