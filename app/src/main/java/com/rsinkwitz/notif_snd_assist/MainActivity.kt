package com.rsinkwitz.notif_snd_assist

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Button
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        checkNotificationListenerPermission()
        findViewById<Button>(R.id.btnTest).setOnClickListener {
            Log.d("Piepton", "TEST button clicked")
            sendTestNotification()
        }
        findViewById<Button>(R.id.btnSeenApps).setOnClickListener {
            val intent = Intent(this, DialogSeenAppsActivity::class.java)
            startActivity(intent)
        }
        findViewById<Button>(R.id.btnPendingApps).setOnClickListener {
            val intent = Intent(this, PendingAppsActivity::class.java)
            startActivity(intent)
        }
    }

    private fun checkNotificationListenerPermission() {
        val cn = ComponentName(this, NotificationMonitorService::class.java)
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        val enabled = flat != null && flat.contains(cn.flattenToString())
        if (!enabled) {
            // Nutzer zu den Einstellungen leiten
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        }
    }

    private fun sendTestNotification() {
        val channelId = "test_channel"
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Test Channel",
                NotificationManager.IMPORTANCE_HIGH
            )
            // Default-Sound explizit setzen
            channel.setSound(android.provider.Settings.System.DEFAULT_NOTIFICATION_URI, null)
            nm.createNotificationChannel(channel)
        }

        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, channelId)
                .setContentTitle("Test Notification")
                .setContentText("Hello from NotificationSoundAssistant")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .build()
        } else {
            Notification.Builder(this)
                .setContentTitle("Test Notification")
                .setContentText("Hello from NotificationSoundAssistant")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .build()
        }

        nm.notify(1001, notification)
    }
}
