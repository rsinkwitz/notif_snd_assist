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

class DialogSeenAppsActivity : AppCompatActivity() {
    private lateinit var adapter: ArrayAdapter<String>
    private lateinit var seenApps: MutableList<String>
    private lateinit var seenKeys: MutableList<String> // packageName:channelId
    private val prefsName = "notif_snd_assist_prefs"
    private val seenAppsKey = "seen_apps"
    private val pendingKey = "pending_apps"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dialog_seen_apps)
        setTitle(R.string.seen_apps_title)

        val prefs = getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        val allSeenApps = (prefs.getStringSet(seenAppsKey, setOf()) ?: setOf()).toMutableList()

        // Filtere unerwünschte Apps heraus (z.B. com.android.systemui)
        val filtered = allSeenApps.filter { key -> !shouldFilterPackage(key) }.sorted()

        // Wenn gefilterte Apps gefunden wurden, aktualisiere die SharedPreferences
        if (filtered.size < allSeenApps.size) {
            prefs.edit().putStringSet(seenAppsKey, filtered.toSet()).apply()
        }

        seenKeys = filtered.toMutableList()
        val pm = packageManager
        // Erzeuge eine Liste mit App-Namen und Channel-IDs
        seenApps = seenKeys.map { key ->
            getAppLabelWithChannel(pm, key)
        }.toMutableList()
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, seenApps)
        val listView = findViewById<ListView>(R.id.listSeenApps)
        listView.adapter = adapter

        listView.setOnItemClickListener { _, _, position, _ ->
            val key = seenKeys[position]
            val appLabel = seenApps[position].substringBefore('\n')
            val packageName = key.substringBefore(':')

            // Zeige Dialog mit vier Optionen: Konfigurieren, Als neu markieren, Entfernen, Abbrechen
            val options = arrayOf(
                getString(R.string.btn_configure),
                getString(R.string.btn_mark_as_new),
                getString(R.string.btn_remove),
                getString(R.string.btn_cancel)
            )

            AlertDialog.Builder(this)
                .setTitle(getString(R.string.dialog_action_for_app, appLabel))
                .setItems(options) { _, which ->
                    when (which) {
                        0 -> {
                            // Konfigurieren: Öffne App-Einstellungen
                            openAppSettings(packageName)
                        }
                        1 -> {
                            // Als neu markieren: Verschiebe von "seen" zu "pending"
                            seenKeys.removeAt(position)
                            prefs.edit().putStringSet(seenAppsKey, seenKeys.toSet()).apply()

                            val pendingApps = prefs.getStringSet(pendingKey, setOf())?.toMutableSet() ?: mutableSetOf()
                            pendingApps.add(key)
                            prefs.edit().putStringSet(pendingKey, pendingApps).apply()

                            seenApps.removeAt(position)
                            adapter.notifyDataSetChanged()
                            updateButtonStates()
                            Toast.makeText(this, getString(R.string.toast_marked_as_new, appLabel), Toast.LENGTH_SHORT).show()
                        }
                        2 -> {
                            // Entfernen: Entferne aus "seen" ohne zu "pending" zu verschieben
                            seenKeys.removeAt(position)
                            prefs.edit().putStringSet(seenAppsKey, seenKeys.toSet()).apply()

                            seenApps.removeAt(position)
                            adapter.notifyDataSetChanged()
                            updateButtonStates()
                            Toast.makeText(this, getString(R.string.toast_removed, appLabel), Toast.LENGTH_SHORT).show()
                        }
                        3 -> {
                            // Abbrechen: Dialog schließen (nichts tun)
                        }
                    }
                }
                .show()
        }

        val btnMarkAllAsNew = findViewById<Button>(R.id.btnMarkAllAsNew)
        val btnRemoveAll = findViewById<Button>(R.id.btnRemoveAll)
        val btnClose = findViewById<Button>(R.id.btnClose)

        // Aktualisiere Button-Status basierend auf Liste
        updateButtonStates()

        btnMarkAllAsNew.setOnClickListener {
            // Verschiebe alle Apps zu "Neue Apps"
            if (seenKeys.isEmpty()) {
                Toast.makeText(this, R.string.toast_no_apps_available, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            AlertDialog.Builder(this)
                .setTitle(R.string.dialog_mark_all_as_new_title)
                .setMessage(getString(R.string.dialog_mark_all_as_new_message, seenKeys.size))
                .setPositiveButton(R.string.btn_yes) { _, _ ->
                    val pendingApps = prefs.getStringSet(pendingKey, setOf())?.toMutableSet() ?: mutableSetOf()
                    pendingApps.addAll(seenKeys)
                    prefs.edit()
                        .putStringSet(pendingKey, pendingApps)
                        .putStringSet(seenAppsKey, setOf())
                        .apply()

                    seenKeys.clear()
                    seenApps.clear()
                    adapter.notifyDataSetChanged()
                    updateButtonStates()
                    Toast.makeText(this, R.string.toast_all_marked_as_new, Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton(R.string.btn_cancel, null)
                .show()
        }

        btnRemoveAll.setOnClickListener {
            // Entferne alle Apps komplett
            if (seenKeys.isEmpty()) {
                Toast.makeText(this, R.string.toast_no_apps_available, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            AlertDialog.Builder(this)
                .setTitle(R.string.dialog_delete_all_title)
                .setMessage(getString(R.string.dialog_delete_all_message, seenKeys.size))
                .setPositiveButton(R.string.btn_yes) { _, _ ->
                    prefs.edit().putStringSet(seenAppsKey, setOf()).apply()

                    seenKeys.clear()
                    seenApps.clear()
                    adapter.notifyDataSetChanged()
                    updateButtonStates()
                    Toast.makeText(this, R.string.toast_all_apps_deleted, Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton(R.string.btn_cancel, null)
                .show()
        }

        btnClose.setOnClickListener {
            finish()
        }
    }

    private fun openAppSettings(pkg: String) {
        val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = android.net.Uri.parse("package:$pkg")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
        Toast.makeText(this, R.string.toast_choose_notification_category, Toast.LENGTH_LONG).show()
    }

    private fun updateButtonStates() {
        val btnMarkAllAsNew = findViewById<Button>(R.id.btnMarkAllAsNew)
        val btnRemoveAll = findViewById<Button>(R.id.btnRemoveAll)

        btnMarkAllAsNew.isEnabled = seenKeys.isNotEmpty()
        btnRemoveAll.isEnabled = seenKeys.isNotEmpty()
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
            val label = pm.getApplicationLabel(packageInfo.applicationInfo).toString()
            android.util.Log.d("Piepton", "App-Name für $pkg: $label (Strategie 1)")
            return label
        } catch (e: Exception) {
            android.util.Log.d("Piepton", "Strategie 1 fehlgeschlagen für $pkg: ${e.message}")
        }

        // Strategie 2: Versuche über alle installierten Packages zu iterieren
        try {
            @Suppress("DEPRECATION")
            val installedApps = pm.getInstalledApplications(0)
            android.util.Log.d("Piepton", "Strategie 2: ${installedApps.size} Apps gefunden, suche nach $pkg")
            val appInfo = installedApps.find { it.packageName == pkg }
            if (appInfo != null) {
                val label = pm.getApplicationLabel(appInfo).toString()
                android.util.Log.d("Piepton", "App-Name für $pkg: $label (Strategie 2 - gefunden!)")
                return label
            } else {
                android.util.Log.w("Piepton", "Strategie 2: $pkg nicht in Liste gefunden")
            }
        } catch (e: Exception) {
            android.util.Log.e("Piepton", "Strategie 2 Exception für $pkg: ${e.message}")
        }

        // Strategie 2a: Versuche mit MATCH_ALL für Multi-User
        try {
            @Suppress("DEPRECATION")
            val installedApps = pm.getInstalledApplications(android.content.pm.PackageManager.MATCH_ALL)
            android.util.Log.d("Piepton", "Strategie 2a (MATCH_ALL): ${installedApps.size} Apps gefunden, suche nach $pkg")
            val appInfo = installedApps.find { it.packageName == pkg }
            if (appInfo != null) {
                val label = pm.getApplicationLabel(appInfo).toString()
                android.util.Log.d("Piepton", "App-Name für $pkg: $label (Strategie 2a MATCH_ALL - gefunden!)")
                return label
            } else {
                android.util.Log.w("Piepton", "Strategie 2a: $pkg nicht in Liste gefunden")
                // Debug: Liste alle Yahoo und Facebook packages
                val yahoofb = installedApps.filter {
                    it.packageName.contains("yahoo", ignoreCase = true) ||
                    it.packageName.contains("facebook", ignoreCase = true) ||
                    it.packageName.contains("whatsapp", ignoreCase = true)
                }
                if (yahoofb.isNotEmpty()) {
                    android.util.Log.d("Piepton", "Gefundene Yahoo/Facebook/WhatsApp Packages: ${yahoofb.map { it.packageName }}")
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("Piepton", "Strategie 2a Exception für $pkg: ${e.message}")
        }

        // Strategie 3: Versuche mit MATCH_UNINSTALLED_PACKAGES (für teilweise deinstallierte Apps)
        try {
            @Suppress("DEPRECATION")
            val packageInfo = pm.getPackageInfo(pkg, android.content.pm.PackageManager.MATCH_UNINSTALLED_PACKAGES)
            val label = pm.getApplicationLabel(packageInfo.applicationInfo).toString()
            android.util.Log.d("Piepton", "App-Name für $pkg: $label (Strategie 3 - deinstalliert)")
            return label
        } catch (e: Exception) {
            android.util.Log.d("Piepton", "Strategie 3 fehlgeschlagen für $pkg: ${e.message}")
        }

        // Fallback: Formatiere den Package-Namen
        android.util.Log.w("Piepton", "Alle Strategien fehlgeschlagen für $pkg, verwende formatierten Namen")
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
