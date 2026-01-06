package com.rsinkwitz.notif_snd_assist

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class NotificationHistoryAdapter(
    private val context: Context,
    private var items: List<NotificationHistoryItem>
) : RecyclerView.Adapter<NotificationHistoryAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvAppName: TextView = view.findViewById(R.id.tvHistoryAppName)
        val tvChannelName: TextView = view.findViewById(R.id.tvHistoryChannelName)
        val tvTimestamp: TextView = view.findViewById(R.id.tvHistoryTimestamp)
    }

    fun updateData(newItems: List<NotificationHistoryItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_notification_history, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]

        // App-Name ermitteln
        val pm = context.packageManager
        val appName = getAppLabel(pm, item.packageName)
        holder.tvAppName.text = appName

        // Channel-Name ermitteln
        val channelName = getChannelName(item.packageName, item.channelId)
        if (channelName != null) {
            holder.tvChannelName.text = channelName
            holder.tvChannelName.visibility = View.VISIBLE
        } else {
            holder.tvChannelName.visibility = View.GONE
        }

        // Zeitdifferenz berechnen
        val now = System.currentTimeMillis()
        val diff = now - item.timestamp
        val seconds = diff / 1000
        val minutes = seconds / 60

        val timeText = when {
            minutes > 0 -> context.getString(R.string.time_format_minutes, minutes, seconds % 60)
            else -> context.getString(R.string.time_format_seconds, seconds)
        }

        holder.tvTimestamp.text = timeText

        // Click-Handler: Öffne App-Einstellungen
        holder.itemView.setOnClickListener {
            // Öffne Benachrichtigungseinstellungen
            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            intent.putExtra(Settings.EXTRA_APP_PACKAGE, item.packageName)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)

            // Verschiebe App von "Neue Apps" zu "Gesehene Apps" falls nötig
            moveAppFromPendingToSeen(item.packageName)
        }
    }

    private fun moveAppFromPendingToSeen(packageName: String) {
        val prefs = context.getSharedPreferences("notif_snd_assist_prefs", Context.MODE_PRIVATE)
        val pendingKey = "pending_apps"
        val seenKey = "seen_apps"

        val pendingApps = prefs.getStringSet(pendingKey, setOf())?.toMutableSet() ?: mutableSetOf()

        // Prüfe ob App in "Neue Apps" ist
        if (pendingApps.contains(packageName)) {
            // Entferne aus "Neue Apps"
            pendingApps.remove(packageName)

            // Füge zu "Gesehene Apps" hinzu
            val seenApps = prefs.getStringSet(seenKey, setOf())?.toMutableSet() ?: mutableSetOf()
            seenApps.add(packageName)

            // Speichere Änderungen
            prefs.edit()
                .putStringSet(pendingKey, pendingApps)
                .putStringSet(seenKey, seenApps)
                .apply()

            Log.d("Piepton", "App $packageName von 'Neue Apps' zu 'Gesehene Apps' verschoben")
        }
    }

    override fun getItemCount() = items.size

    private fun getAppLabel(pm: android.content.pm.PackageManager, pkg: String): String {
        // Strategie 1: Versuche getPackageInfo
        try {
            @Suppress("DEPRECATION")
            val packageInfo = pm.getPackageInfo(pkg, 0)
            return pm.getApplicationLabel(packageInfo.applicationInfo).toString()
        } catch (e: Exception) {
            // Weiter zur nächsten Strategie
        }

        // Strategie 2: Versuche über alle installierten Packages zu iterieren
        try {
            @Suppress("DEPRECATION")
            val installedApps = pm.getInstalledApplications(0)
            val appInfo = installedApps.find { it.packageName == pkg }
            if (appInfo != null) {
                return pm.getApplicationLabel(appInfo).toString()
            }
        } catch (e: Exception) {
            // Weiter zur nächsten Strategie
        }

        // Strategie 3: Versuche mit MATCH_UNINSTALLED_PACKAGES
        try {
            @Suppress("DEPRECATION")
            val packageInfo = pm.getPackageInfo(pkg, android.content.pm.PackageManager.MATCH_UNINSTALLED_PACKAGES)
            return pm.getApplicationLabel(packageInfo.applicationInfo).toString()
        } catch (e: Exception) {
            // Alle Strategien fehlgeschlagen
        }

        // Fallback: Formatiere den Package-Namen
        return formatPackageName(pkg)
    }

    private fun formatPackageName(packageName: String): String {
        // Extrahiere den letzten Teil des Package-Namens und mache ihn lesbar
        // z.B. "com.yahoo.mobile.client.android.mail" -> "Yahoo Mail"
        return when {
            packageName.contains("yahoo") && packageName.contains("mail") -> "Yahoo Mail"
            packageName.contains("whatsapp") -> "WhatsApp"
            packageName.contains("gmail") -> "Gmail"
            packageName.contains("facebook") -> "Facebook"
            packageName.contains("instagram") -> "Instagram"
            packageName.contains("twitter") -> "Twitter"
            packageName.contains("telegram") -> "Telegram"
            packageName.contains("signal") -> "Signal"
            packageName.contains("messenger") -> "Messenger"
            else -> {
                // Fallback: Nimm den letzten Teil nach dem letzten Punkt und kapitalisiere
                val parts = packageName.split(".")
                val lastPart = parts.lastOrNull() ?: packageName
                lastPart.replaceFirstChar { it.uppercase() }
            }
        }
    }

    private fun getChannelName(packageName: String, channelId: String?): String? {
        if (channelId == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return null
        }

        // Für die eigene App können wir den Channel direkt lesen
        if (packageName == context.packageName) {
            try {
                @Suppress("CAST_NEVER_SUCCEEDS")
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val channel = notificationManager.getNotificationChannel(channelId)
                return channel?.name?.toString()
            } catch (e: Exception) {
                Log.w("Piepton", "Fehler beim Abrufen des Channel-Namens für eigene App: $channelId", e)
            }
        }

        // Für fremde Apps können wir den Channel nicht direkt lesen
        // Wir geben die channelId zurück, formatiert
        return formatChannelId(channelId)
    }

    private fun formatChannelId(channelId: String): String? {
        // Wenn die Channel-ID nur aus Zahlen besteht, nicht anzeigen
        if (channelId.all { it.isDigit() }) {
            return null
        }

        // Wenn die Channel-ID sehr kurz ist (< 3 Zeichen), nicht anzeigen
        if (channelId.length < 3) {
            return null
        }

        // Formatiere die Channel-ID etwas lesbarer
        // z.B. "500_mail__people_" -> "Mail People"
        val formatted = channelId
            .replace("_", " ")
            .split(" ")
            .filter { it.isNotEmpty() && !it.all { char -> char.isDigit() } }
            .joinToString(" ") { word ->
                word.replaceFirstChar { it.uppercase() }
            }
            .trim()

        // Wenn nach dem Formatieren nichts übrig bleibt, nicht anzeigen
        return if (formatted.isEmpty() || formatted.length < 3) null else formatted
    }
}

