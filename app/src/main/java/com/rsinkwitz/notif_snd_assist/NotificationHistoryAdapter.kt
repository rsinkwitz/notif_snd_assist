package com.rsinkwitz.notif_snd_assist

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
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

        // Zeitdifferenz berechnen
        val now = System.currentTimeMillis()
        val diff = now - item.timestamp
        val seconds = diff / 1000
        val minutes = seconds / 60

        val timeText = when {
            minutes > 0 -> String.format("vor %d:%02d min", minutes, seconds % 60)
            else -> String.format("vor %d sec", seconds)
        }

        holder.tvTimestamp.text = timeText

        // Click-Handler: Öffne App-Einstellungen
        holder.itemView.setOnClickListener {
            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            intent.putExtra(Settings.EXTRA_APP_PACKAGE, item.packageName)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
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
}

