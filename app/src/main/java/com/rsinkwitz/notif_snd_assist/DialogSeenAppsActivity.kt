package com.rsinkwitz.notif_snd_assist

import android.app.AlertDialog
import android.content.Context
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
    private val prefsName = "notif_snd_assist_prefs"
    private val seenAppsKey = "seen_apps"
    private val pendingKey = "pending_apps"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dialog_seen_apps)
        setTitle("Gesehene Apps")

        val prefs = getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        val allSeenApps = (prefs.getStringSet(seenAppsKey, setOf()) ?: setOf()).toMutableList()

        // Filtere unerwünschte Apps heraus (z.B. com.android.systemui)
        val filtered = allSeenApps.filter { pkg -> !shouldFilterPackage(pkg) }.sorted()

        // Wenn gefilterte Apps gefunden wurden, aktualisiere die SharedPreferences
        if (filtered.size < allSeenApps.size) {
            prefs.edit().putStringSet(seenAppsKey, filtered.toSet()).apply()
        }

        val seenAppsPkgs = filtered.toMutableList()
        val pm = packageManager
        // Erzeuge eine Liste mit nur App-Namen (Label)
        seenApps = seenAppsPkgs.map { pkg ->
            try {
                @Suppress("DEPRECATION")
                val appInfo = pm.getApplicationInfo(pkg, PackageManager.MATCH_UNINSTALLED_PACKAGES)
                val label = pm.getApplicationLabel(appInfo).toString()
                android.util.Log.d("Piepton", "App-Name für $pkg: $label")
                label
            } catch (e: Exception) {
                android.util.Log.w("Piepton", "App nicht gefunden, verwende formatierten Namen für $pkg")
                formatPackageName(pkg)
            }
        }.toMutableList()
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, seenApps)
        val listView = findViewById<ListView>(R.id.listSeenApps)
        listView.adapter = adapter

        listView.setOnItemClickListener { _, _, position, _ ->
            val pkg = seenAppsPkgs[position]
            val appLabel = seenApps[position]

            // Prüfe ob die App noch installiert ist
            val isInstalled = try {
                @Suppress("DEPRECATION")
                pm.getApplicationInfo(pkg, 0)
                true
            } catch (e: Exception) {
                false
            }

            if (isInstalled) {
                // App ist installiert - biete "Als neu markieren" an
                AlertDialog.Builder(this)
                    .setTitle("Als neu markieren?")
                    .setMessage("$appLabel zurück zu 'Neue Apps' verschieben?")
                    .setPositiveButton("Als neu markieren") { _, _ ->
                        // Verschiebe von "seen" zu "pending"
                        seenAppsPkgs.removeAt(position)
                        prefs.edit().putStringSet(seenAppsKey, seenAppsPkgs.toSet()).apply()

                        val pendingApps = prefs.getStringSet(pendingKey, setOf())?.toMutableSet() ?: mutableSetOf()
                        pendingApps.add(pkg)
                        prefs.edit().putStringSet(pendingKey, pendingApps).apply()

                        seenApps.removeAt(position)
                        adapter.notifyDataSetChanged()
                        Toast.makeText(this, "$appLabel als neu markiert", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Abbrechen", null)
                    .show()
            } else {
                // App ist deinstalliert - biete "Löschen" an
                AlertDialog.Builder(this)
                    .setTitle("Eintrag löschen?")
                    .setMessage("$appLabel ist nicht mehr installiert. Eintrag entfernen?")
                    .setPositiveButton("Löschen") { _, _ ->
                        // Entferne aus "seen"
                        seenAppsPkgs.removeAt(position)
                        prefs.edit().putStringSet(seenAppsKey, seenAppsPkgs.toSet()).apply()

                        seenApps.removeAt(position)
                        adapter.notifyDataSetChanged()
                        Toast.makeText(this, "$appLabel entfernt", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Abbrechen", null)
                    .show()
            }
        }

        findViewById<Button>(R.id.btnClearAll).setOnClickListener {
            // Zähle deinstallierte Apps
            val uninstalledApps = seenAppsPkgs.filterIndexed { index, pkg ->
                try {
                    @Suppress("DEPRECATION")
                    pm.getApplicationInfo(pkg, 0)
                    false // installiert
                } catch (e: Exception) {
                    true // deinstalliert
                }
            }

            if (uninstalledApps.isEmpty()) {
                // Keine deinstallierten Apps - biete "Alle als neu markieren" an
                AlertDialog.Builder(this)
                    .setTitle("Alle als neu markieren?")
                    .setMessage("Wirklich alle Apps zurück zu 'Neue Apps' verschieben?")
                    .setPositiveButton("Ja") { _, _ ->
                        // Verschiebe alle von "seen" zu "pending"
                        val pendingApps = prefs.getStringSet(pendingKey, setOf())?.toMutableSet() ?: mutableSetOf()
                        pendingApps.addAll(seenAppsPkgs)
                        prefs.edit()
                            .putStringSet(pendingKey, pendingApps)
                            .putStringSet(seenAppsKey, setOf())
                            .apply()

                        seenAppsPkgs.clear()
                        seenApps.clear()
                        adapter.notifyDataSetChanged()
                        Toast.makeText(this, "Alle Apps als neu markiert", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Abbrechen", null)
                    .show()
            } else {
                // Es gibt deinstallierte Apps - biete Optionen an
                AlertDialog.Builder(this)
                    .setTitle("Optionen")
                    .setMessage("${uninstalledApps.size} deinstallierte App(s) gefunden")
                    .setPositiveButton("Alle als neu markieren") { _, _ ->
                        // Verschiebe nur installierte Apps zu "pending"
                        val installedApps = seenAppsPkgs.filter { pkg ->
                            try {
                                @Suppress("DEPRECATION")
                                pm.getApplicationInfo(pkg, 0)
                                true
                            } catch (e: Exception) {
                                false
                            }
                        }
                        val pendingApps = prefs.getStringSet(pendingKey, setOf())?.toMutableSet() ?: mutableSetOf()
                        pendingApps.addAll(installedApps)
                        prefs.edit()
                            .putStringSet(pendingKey, pendingApps)
                            .putStringSet(seenAppsKey, setOf())
                            .apply()

                        seenAppsPkgs.clear()
                        seenApps.clear()
                        adapter.notifyDataSetChanged()
                        Toast.makeText(this, "Alle Apps als neu markiert", Toast.LENGTH_SHORT).show()
                    }
                    .setNeutralButton("Deinstallierte löschen") { _, _ ->
                        // Entferne nur deinstallierte Apps
                        val installedApps = seenAppsPkgs.filterIndexed { index, pkg ->
                            try {
                                @Suppress("DEPRECATION")
                                pm.getApplicationInfo(pkg, 0)
                                true
                            } catch (e: Exception) {
                                false
                            }
                        }
                        prefs.edit().putStringSet(seenAppsKey, installedApps.toSet()).apply()

                        // Aktualisiere die Listen
                        seenAppsPkgs.clear()
                        seenAppsPkgs.addAll(installedApps)
                        seenApps.clear()
                        seenApps.addAll(installedApps.map { pkg ->
                            try {
                                @Suppress("DEPRECATION")
                                val appInfo = pm.getApplicationInfo(pkg, PackageManager.MATCH_UNINSTALLED_PACKAGES)
                                pm.getApplicationLabel(appInfo).toString()
                            } catch (e: Exception) {
                                formatPackageName(pkg)
                            }
                        })
                        adapter.notifyDataSetChanged()
                        Toast.makeText(this, "${uninstalledApps.size} deinstallierte App(s) entfernt", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Abbrechen", null)
                    .show()
            }
        }
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
