package com.rsinkwitz.notif_snd_assist

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Button
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONArray

class MainActivity : AppCompatActivity() {

    private lateinit var rvHistory: RecyclerView
    private lateinit var historyAdapter: NotificationHistoryAdapter

    private val notificationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            // Aktualisiere die History-Liste wenn eine neue Benachrichtigung kommt
            Log.d("Piepton", "Broadcast empfangen - aktualisiere History")
            loadNotificationHistory()
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Prüfe ob Onboarding bereits abgeschlossen wurde
        val prefs = getSharedPreferences("notif_snd_assist_prefs", Context.MODE_PRIVATE)
        val onboardingCompleted = prefs.getBoolean("onboarding_completed", false)

        if (!onboardingCompleted) {
            // Zeige Onboarding
            val intent = Intent(this, OnboardingActivity::class.java)
            startActivity(intent)
            finish()
            return
        }

        setContentView(R.layout.activity_main)
        setTitle(R.string.main_title)
        checkNotificationListenerPermission()

        // RecyclerView initialisieren
        rvHistory = findViewById(R.id.rvNotificationHistory)
        rvHistory.layoutManager = LinearLayoutManager(this)

        findViewById<Button>(R.id.btnSeenApps).setOnClickListener {
            val intent = Intent(this, DialogSeenAppsActivity::class.java)
            startActivity(intent)
        }
        findViewById<Button>(R.id.btnPendingApps).setOnClickListener {
            val intent = Intent(this, PendingAppsActivity::class.java)
            startActivity(intent)
        }
        findViewById<Button>(R.id.btnHelp).setOnClickListener {
            val intent = Intent(this, HelpActivity::class.java)
            startActivity(intent)
        }

        loadNotificationHistory()
    }

    override fun onResume() {
        super.onResume()
        // History neu laden wenn Activity wieder in den Vordergrund kommt
        loadNotificationHistory()

        // Registriere BroadcastReceiver für Echtzeit-Updates
        val filter = IntentFilter("com.rsinkwitz.notif_snd_assist.NEW_NOTIFICATION")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(notificationReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(notificationReceiver, filter)
        }
        Log.d("Piepton", "BroadcastReceiver registriert")
    }

    override fun onPause() {
        super.onPause()
        // Deregistriere BroadcastReceiver
        try {
            unregisterReceiver(notificationReceiver)
            Log.d("Piepton", "BroadcastReceiver deregistriert")
        } catch (e: Exception) {
            Log.e("Piepton", "Fehler beim Deregistrieren des BroadcastReceivers", e)
        }
    }

    private fun loadNotificationHistory() {
        val prefs = getSharedPreferences("notif_snd_assist_prefs", Context.MODE_PRIVATE)
        val historyJson = prefs.getString("notification_history", "[]") ?: "[]"

        val items = mutableListOf<NotificationHistoryItem>()
        try {
            val history = JSONArray(historyJson)
            for (i in 0 until history.length()) {
                val entry = history.getJSONObject(i)
                val packageName = entry.getString("packageName")
                val timestamp = entry.getLong("timestamp")
                val channelId = if (entry.has("channelId")) entry.getString("channelId") else null
                items.add(NotificationHistoryItem(packageName, timestamp, channelId))
            }
        } catch (e: Exception) {
            Log.e("Piepton", "Error loading notification history", e)
        }

        // Erstelle Adapter nur einmal oder aktualisiere die Daten
        if (!::historyAdapter.isInitialized) {
            historyAdapter = NotificationHistoryAdapter(this, items)
            rvHistory.adapter = historyAdapter
            Log.d("Piepton", "History-Adapter erstellt mit ${items.size} Einträgen")
        } else {
            historyAdapter.updateData(items)
            Log.d("Piepton", "History-Adapter aktualisiert mit ${items.size} Einträgen")
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
}
