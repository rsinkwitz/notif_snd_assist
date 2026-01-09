package com.rsinkwitz.notif_snd_assist

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class PendingAppsActivity : AppCompatActivity() {
    private lateinit var adapter: ArrayAdapter<String>
    private lateinit var pendingKeys: MutableList<String> // packageName:channelId
    private lateinit var pendingLabels: MutableList<String>
    private val prefsName = "notif_snd_assist_prefs"
    private val pendingKey = "pending_apps"
    private val seenKey = "seen_apps"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dialog_seen_apps)
        setTitle(R.string.pending_apps_title)

        val prefs = getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        val allPending = (prefs.getStringSet(pendingKey, setOf()) ?: setOf()).toMutableList()

        // Filtere unerwünschte Apps heraus (z.B. com.android.systemui)
        val filtered = allPending.filter { key -> !shouldFilterPackage(key) }.sorted()

        // Wenn gefilterte Apps gefunden wurden, aktualisiere die SharedPreferences
        if (filtered.size < allPending.size) {
            prefs.edit().putStringSet(pendingKey, filtered.toSet()).apply()
        }

        pendingKeys = filtered.toMutableList()
        val pm = packageManager
        pendingLabels = pendingKeys.map { key ->
            getAppLabelWithChannel(pm, key)
        }.toMutableList()
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, pendingLabels)
        val listView = findViewById<ListView>(R.id.listSeenApps)
        listView.adapter = adapter

        listView.setOnItemClickListener { _, _, position, _ ->
            val key = pendingKeys[position]
            val appLabel = pendingLabels[position].substringBefore('\n')
            val packageName = key.substringBefore(':')
            AlertDialog.Builder(this)
                .setTitle(R.string.dialog_open_settings)
                .setMessage(getString(R.string.dialog_open_settings_message, appLabel))
                .setPositiveButton(R.string.dialog_open_settings) { _, _ ->
                    openAppSettings(packageName)
                    // Nach Öffnen als "gesehen" markieren und aus pending entfernen
                    val prefsEdit = prefs.edit()
                    val newPending = pendingKeys.toMutableSet()
                    newPending.remove(key)
                    prefsEdit.putStringSet(pendingKey, newPending)
                    val seen = prefs.getStringSet(seenKey, setOf())?.toMutableSet() ?: mutableSetOf()
                    seen.add(key)
                    prefsEdit.putStringSet(seenKey, seen)
                    prefsEdit.apply()
                    pendingKeys.removeAt(position)
                    pendingLabels.removeAt(position)
                    adapter.notifyDataSetChanged()
                    updateButtonStates()
                    Toast.makeText(this, getString(R.string.toast_marked_as_done, appLabel), Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton(R.string.btn_cancel, null)
                .show()
        }

        // Blende "Alle als neu markieren" aus - macht für "Neue Apps" keinen Sinn
        findViewById<Button>(R.id.btnMarkAllAsNew).visibility = android.view.View.GONE

        val btnRemoveAll = findViewById<Button>(R.id.btnRemoveAll)
        val btnClose = findViewById<Button>(R.id.btnClose)

        // Aktualisiere Button-Status basierend auf Liste
        updateButtonStates()

        // "Alle löschen" Button
        btnRemoveAll.setOnClickListener {
            if (pendingKeys.isEmpty()) {
                Toast.makeText(this, R.string.toast_no_apps_available, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            AlertDialog.Builder(this)
                .setTitle(R.string.dialog_delete_all_title)
                .setMessage(getString(R.string.dialog_delete_all_message, pendingKeys.size))
                .setPositiveButton(R.string.btn_yes) { _, _ ->
                    prefs.edit().putStringSet(pendingKey, setOf()).apply()
                    pendingKeys.clear()
                    pendingLabels.clear()
                    adapter.notifyDataSetChanged()
                    updateButtonStates()
                    Toast.makeText(this, R.string.toast_all_apps_deleted, Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton(R.string.btn_cancel, null)
                .show()
        }

        // "Schließen" Button
        btnClose.setOnClickListener {
            finish()
        }
    }

    private fun updateButtonStates() {
        val btnRemoveAll = findViewById<Button>(R.id.btnRemoveAll)
        btnRemoveAll.isEnabled = pendingKeys.isNotEmpty()
    }

    private fun openAppSettings(pkg: String) {
        val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = android.net.Uri.parse("package:$pkg")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
        Toast.makeText(this, R.string.toast_choose_notification_category, Toast.LENGTH_LONG).show()
    }

    private fun shouldFilterPackage(key: String): Boolean {
        // Extrahiere packageName aus key (vor dem Doppelpunkt)
        val packageName = key.substringBefore(':')

        // Filtere System-UI und andere unerwünschte Apps
        val ignoredPackages = listOf(
            "com.android.systemui",
            "android",
            "com.android.system"
        )
        return ignoredPackages.contains(packageName)
    }

    private fun getAppLabelWithChannel(pm: android.content.pm.PackageManager, key: String): String {
        val parts = key.split(':')
        val packageName = parts[0]
        val channelId = if (parts.size > 1) parts[1] else null

        val appName = getAppLabel(pm, packageName)

        // Wenn ein channelId vorhanden ist, formatiere und zeige ihn an
        if (channelId != null && channelId.isNotEmpty()) {
            val formattedChannel = ChannelFormatter.formatChannelId(channelId)
            return if (formattedChannel != null) {
                "$appName\n($formattedChannel)"
            } else {
                appName
            }
        }

        return appName
    }

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
