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
            val channelName = "Benachrichtigungston-Assistent Dienst"
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(channelId) == null) {
                val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW)
                nm.createNotificationChannel(channel)
            }
            val notification = Notification.Builder(this, channelId)
                .setContentTitle("Benachrichtigungston-Assistent läuft")
                .setContentText("Benachrichtigungen werden überwacht")
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
                .build()
            startForeground(1, notification)
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName

        // Hole den Titel der Benachrichtigung
        val notification = sbn.notification
        val title = notification?.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = notification?.extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""

        Log.d("Piepton", "Notification from $packageName, title: '$title', text: '$text'")

        // Filtere unerwünschte Benachrichtigungen heraus
        if (shouldIgnoreNotification(packageName, title, text)) {
            Log.d("Piepton", "Benachrichtigung wird ignoriert: $packageName - '$title'")
            return
        }

        // Prüfe ob die Benachrichtigung einen Sound hat
        if (!hasSound(notification, packageName)) {
            Log.d("Piepton", "Benachrichtigung ohne Sound wird ignoriert: $packageName")
            return
        }

        // Hole channelId für Android O und höher, oder category als Fallback
        val channelId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notification?.channelId
        } else {
            null
        }

        // Verwende category als Fallback wenn kein channelId vorhanden ist
        val category = notification?.category
        val effectiveChannelId = channelId ?: category

        Log.d("Piepton", "channelId: $channelId, category: $category, effectiveChannelId: $effectiveChannelId")

        val prefs = getSharedPreferences("notif_snd_assist_prefs", Context.MODE_PRIVATE)
        val seenApps = prefs.getStringSet("seen_apps", mutableSetOf()) ?: mutableSetOf()
        val pendingKey = "pending_apps"
        val pendingApps = prefs.getStringSet(pendingKey, mutableSetOf()) ?: mutableSetOf()

        // Speichere die Benachrichtigung in der History (letzte 5)
        saveNotificationToHistory(packageName, effectiveChannelId)

        // Erstelle einen eindeutigen Key: packageName:channelId (oder category als Fallback)
        val channelKey = if (effectiveChannelId != null) {
            "$packageName:$effectiveChannelId"
        } else {
            packageName // Fallback wenn weder channelId noch category vorhanden
        }

        if (!seenApps.contains(channelKey) && !pendingApps.contains(channelKey)) {
            // Diese App:Channel-Kombination wurde noch nicht behandelt und ist nicht in pending
            val newPending = pendingApps.toMutableSet()
            newPending.add(channelKey)
            prefs.edit().putStringSet(pendingKey, newPending).apply()
            Log.i("Piepton", "Neue App:Channel in pending-Liste: $channelKey")
            // Kein automatisches Öffnen mehr!
            return
        }
        Log.d("Piepton", "App:Channel bereits bekannt: $channelKey (in seenApps: ${seenApps.contains(channelKey)}, in pendingApps: ${pendingApps.contains(channelKey)})")
    }

    private fun shouldIgnoreNotification(packageName: String, title: String, text: String): Boolean {
        // Ignoriere eigene App (außer Test-Benachrichtigungen)
        if (packageName == "com.rsinkwitz.notif_snd_assist") {
            // Nur Test-Benachrichtigungen durchlassen (Titel: "Test", Text: "Test-Benachrichtigung")
            if (title == "Test" && text == "Test-Benachrichtigung") {
                return false
            }
            // Alle anderen Benachrichtigungen der eigenen App ignorieren (z.B. Foreground Service)
            return true
        }

        // Ignoriere System-UI Benachrichtigungen
        if (packageName == "com.android.systemui") {
            return true
        }

        // Ignoriere System-Packages
        val ignoredPackages = listOf(
            "android",
            "com.android.system"
        )
        if (ignoredPackages.contains(packageName)) {
            return true
        }

        // Ignoriere Benachrichtigungen mit bestimmten Titeln
        val ignoredTitles = listOf(
            "Oberfläche",
            "Surface",
            "Bildschirmaufnahme",
            "Screen recording",
            "Bildschirm wird übertragen",
            "Screen is being cast",
            "Android System",
            "läuft" // z.B. "TonAssistent läuft"
        )

        for (ignoredTitle in ignoredTitles) {
            if (title.contains(ignoredTitle, ignoreCase = true) || text.contains(ignoredTitle, ignoreCase = true)) {
                return true
            }
        }

        return false
    }

    private fun hasSound(notification: Notification?, packageName: String): Boolean {
        if (notification == null) {
            Log.d("Piepton", "Notification ist null")
            return false
        }

        // Prüfe explizite Sound-Einstellungen
        val defaults = notification.defaults
        val hasExplicitSound = notification.sound != null
        val hasDefaultSound = (defaults and Notification.DEFAULT_SOUND) != 0

        // Wenn explizit ein Sound gesetzt ist, dann hat die Benachrichtigung Sound
        if (hasExplicitSound || hasDefaultSound) {
            Log.d("Piepton", "Benachrichtigung hat expliziten Sound: $packageName")
            return true
        }

        // Ab Android O: Prüfe den NotificationChannel
        // WICHTIG: Wir können nur Channels der eigenen App lesen!
        // Für fremde Apps nehmen wir an, dass sie Sound haben, wenn ein channelId vorhanden ist
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = notification.channelId

            // Wenn kein Channel angegeben ist, hat die Benachrichtigung wahrscheinlich keinen Sound
            if (channelId == null) {
                Log.d("Piepton", "Kein ChannelId vorhanden für $packageName")
                return false
            }

            // Für fremde Apps können wir den Channel nicht lesen
            // Wenn die Benachrichtigung gesendet wurde und einen channelId hat, nehmen wir an, dass sie Sound hat
            Log.d("Piepton", "Benachrichtigung von fremder App (Channel nicht lesbar), nehme Sound an: $packageName")
            return true
        }

        // Vor Android O: Keine Channel-Unterstützung
        // Wenn weder Sound noch Default-Sound gesetzt ist, hat die Benachrichtigung keinen Sound
        Log.d("Piepton", "Keine Sound-Konfiguration gefunden für $packageName (pre-O)")
        return false
    }

    private fun saveNotificationToHistory(packageName: String, channelId: String?) {
        val prefs = getSharedPreferences("notif_snd_assist_prefs", Context.MODE_PRIVATE)
        val historyKey = "notification_history"
        val historyJson = prefs.getString(historyKey, "[]")

        // Parse JSON array
        val history = try {
            org.json.JSONArray(historyJson)
        } catch (e: Exception) {
            org.json.JSONArray()
        }

        // Füge neue Benachrichtigung hinzu
        val newEntry = org.json.JSONObject()
        newEntry.put("packageName", packageName)
        newEntry.put("timestamp", System.currentTimeMillis())
        if (channelId != null) {
            newEntry.put("channelId", channelId)
        }

        // Erstelle neue History und füge den neuen Eintrag am Anfang hinzu
        val newHistory = org.json.JSONArray()
        newHistory.put(newEntry)

        // Kopiere die alten Einträge (maximal 4, damit wir insgesamt 5 haben)
        // Überspringe dabei Einträge mit demselben packageName (um Duplikate zu vermeiden)
        var count = 0
        for (i in 0 until history.length()) {
            if (count >= 4) break

            val oldEntry = history.getJSONObject(i)
            val oldPackageName = oldEntry.getString("packageName")

            // Überspringe Einträge mit demselben packageName
            if (oldPackageName != packageName) {
                newHistory.put(oldEntry)
                count++
            }
        }

        // Speichere zurück
        prefs.edit().putString(historyKey, newHistory.toString()).apply()

        Log.d("Piepton", "History aktualisiert: $packageName an Position 0, ${newHistory.length()} Einträge insgesamt")

        // Sende Broadcast um MainActivity zu informieren
        val intent = Intent("com.rsinkwitz.notif_snd_assist.NEW_NOTIFICATION")
        sendBroadcast(intent)
        Log.d("Piepton", "Broadcast gesendet für neue Benachrichtigung von $packageName")
    }
}
