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
    private lateinit var pendingPkgs: MutableList<String>
    private lateinit var pendingLabels: MutableList<String>
    private val prefsName = "notif_snd_assist_prefs"
    private val pendingKey = "pending_apps"
    private val seenKey = "seen_apps"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dialog_seen_apps)
        setTitle("Neue Apps")

        val prefs = getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        val allPending = (prefs.getStringSet(pendingKey, setOf()) ?: setOf()).toMutableList()

        // Filtere unerwünschte Apps heraus (z.B. com.android.systemui)
        val filtered = allPending.filter { pkg -> !shouldFilterPackage(pkg) }.sorted()

        // Wenn gefilterte Apps gefunden wurden, aktualisiere die SharedPreferences
        if (filtered.size < allPending.size) {
            prefs.edit().putStringSet(pendingKey, filtered.toSet()).apply()
        }

        pendingPkgs = filtered.toMutableList()
        val pm = packageManager
        pendingLabels = pendingPkgs.map { pkg ->
            getAppLabel(pm, pkg)
        }.toMutableList()
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, pendingLabels)
        val listView = findViewById<ListView>(R.id.listSeenApps)
        listView.adapter = adapter

        listView.setOnItemClickListener { _, _, position, _ ->
            val pkg = pendingPkgs[position]
            val appLabel = pendingLabels[position].substringBefore('\n')
            AlertDialog.Builder(this)
                .setTitle("Einstellungen öffnen?")
                .setMessage("Benachrichtigungs-Einstellungen für $appLabel öffnen?")
                .setPositiveButton("Öffnen") { _, _ ->
                    openAppSettings(pkg)
                    // Nach Öffnen als "gesehen" markieren und aus pending entfernen
                    val prefsEdit = prefs.edit()
                    val newPending = pendingPkgs.toMutableSet()
                    newPending.remove(pkg)
                    prefsEdit.putStringSet(pendingKey, newPending)
                    val seen = prefs.getStringSet(seenKey, setOf())?.toMutableSet() ?: mutableSetOf()
                    seen.add(pkg)
                    prefsEdit.putStringSet(seenKey, seen)
                    prefsEdit.apply()
                    pendingPkgs.removeAt(position)
                    pendingLabels.removeAt(position)
                    adapter.notifyDataSetChanged()
                    Toast.makeText(this, "$appLabel wurde als erledigt markiert", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Abbrechen", null)
                .show()
        }

        // Blende "Alle als neu markieren" aus - macht für "Neue Apps" keinen Sinn
        findViewById<Button>(R.id.btnMarkAllAsNew).visibility = android.view.View.GONE

        // Nur "Alle löschen" Button
        findViewById<Button>(R.id.btnRemoveAll).setOnClickListener {
            if (pendingPkgs.isEmpty()) {
                Toast.makeText(this, "Keine Apps vorhanden", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            AlertDialog.Builder(this)
                .setTitle("Alle löschen?")
                .setMessage("Wirklich alle ${pendingPkgs.size} App(s) komplett löschen?")
                .setPositiveButton("Ja") { _, _ ->
                    prefs.edit().putStringSet(pendingKey, setOf()).apply()
                    pendingPkgs.clear()
                    pendingLabels.clear()
                    adapter.notifyDataSetChanged()
                    Toast.makeText(this, "Alle Apps wurden gelöscht", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Abbrechen", null)
                .show()
        }
    }

    private fun openAppSettings(pkg: String) {
        val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = android.net.Uri.parse("package:$pkg")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
        Toast.makeText(this, "Wähle 'Benachrichtigungen' und dann die passende Kategorie, um den Ton zu ändern.", Toast.LENGTH_LONG).show()
    }

    private fun shouldFilterPackage(packageName: String): Boolean {
        // Filtere System-UI und andere unerwünschte Apps
        val ignoredPackages = listOf(
            "com.android.systemui",
            "android",
            "com.android.system"
        )
        return ignoredPackages.contains(packageName)
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
