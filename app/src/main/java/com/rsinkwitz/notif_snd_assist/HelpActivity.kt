package com.rsinkwitz.notif_snd_assist

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.text.Html
import android.text.method.LinkMovementMethod
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class HelpActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_help)
        setTitle(R.string.help_title)

        val tvHelpContent = findViewById<TextView>(R.id.tvHelpContent)

        // HTML-Content setzen
        val helpText = getString(R.string.help_content)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            tvHelpContent.text = Html.fromHtml(helpText, Html.FROM_HTML_MODE_LEGACY)
        } else {
            @Suppress("DEPRECATION")
            tvHelpContent.text = Html.fromHtml(helpText)
        }
        tvHelpContent.movementMethod = LinkMovementMethod.getInstance()

        findViewById<Button>(R.id.btnTestNotification).setOnClickListener {
            Log.d("Piepton", "TEST button clicked")
            sendTestNotification()
        }

        findViewById<Button>(R.id.btnHelpClose).setOnClickListener {
            finish()
        }
    }

    private fun sendTestNotification() {
        val channelId = "test_channel"
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                channelId,
                getString(R.string.test_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            )
            channel.description = getString(R.string.test_channel_description)
            // Default-Sound explizit setzen
            channel.setSound(android.provider.Settings.System.DEFAULT_NOTIFICATION_URI, null)
            nm.createNotificationChannel(channel)
        }

        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, channelId)
                .setContentTitle(getString(R.string.test_notification_title))
                .setContentText(getString(R.string.test_notification_text))
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle(getString(R.string.test_notification_title))
                .setContentText(getString(R.string.test_notification_text))
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setDefaults(Notification.DEFAULT_SOUND)
                .build()
        }
        nm.notify(1001, notification)
    }
}

