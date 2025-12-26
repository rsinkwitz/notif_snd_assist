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
            getAppLabel(pm, pkg)
        }.toMutableList()
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, seenApps)
        val listView = findViewById<ListView>(R.id.listSeenApps)
        listView.adapter = adapter

        listView.setOnItemClickListener { _, _, position, _ ->
            val pkg = seenAppsPkgs[position]
            val appLabel = seenApps[position]


            // Zeige Dialog mit drei Optionen: Als neu markieren, Entfernen, Abbrechen
            AlertDialog.Builder(this)
                .setTitle("Optionen für $appLabel")
                .setMessage("Was möchten Sie tun?")
                .setPositiveButton("Als neu markieren") { _, _ ->
                    // Verschiebe von "seen" zu "pending"
                    seenAppsPkgs.removeAt(position)
                    prefs.edit().putStringSet(seenAppsKey, seenAppsPkgs.toSet()).apply()

                    val pendingApps = prefs.getStringSet(pendingKey, setOf())?.toMutableSet() ?: mutableSetOf()
                    pendingApps.add(pkg)
                    prefs.edit().putStringSet(pendingKey, pendingApps).apply()

                    seenApps.removeAt(position)
                    adapter.notifyDataSetChanged()
                    Toast.makeText(this, "$appLabel wurde als neu markiert", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Entfernen") { _, _ ->
                    // Entferne aus "seen" ohne zu "pending" zu verschieben
                    seenAppsPkgs.removeAt(position)
                    prefs.edit().putStringSet(seenAppsKey, seenAppsPkgs.toSet()).apply()

                    seenApps.removeAt(position)
                    adapter.notifyDataSetChanged()
                    Toast.makeText(this, "$appLabel wurde entfernt", Toast.LENGTH_SHORT).show()
                }
                .setNeutralButton("Abbrechen", null)
                .show()
        }

        findViewById<Button>(R.id.btnMarkAllAsNew).setOnClickListener {
            // Verschiebe alle Apps zu "Neue Apps"
            if (seenAppsPkgs.isEmpty()) {
                Toast.makeText(this, "Keine Apps vorhanden", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            AlertDialog.Builder(this)
                .setTitle("Alle als neu markieren?")
                .setMessage("Wirklich alle ${seenAppsPkgs.size} App(s) zurück zu 'Neue Apps' verschieben?")
                .setPositiveButton("Ja") { _, _ ->
                    val pendingApps = prefs.getStringSet(pendingKey, setOf())?.toMutableSet() ?: mutableSetOf()
                    pendingApps.addAll(seenAppsPkgs)
                    prefs.edit()
                        .putStringSet(pendingKey, pendingApps)
                        .putStringSet(seenAppsKey, setOf())
                        .apply()

                    seenAppsPkgs.clear()
                    seenApps.clear()
                    adapter.notifyDataSetChanged()
                    Toast.makeText(this, "Alle Apps wurden als neu markiert", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Abbrechen", null)
                .show()
        }

        findViewById<Button>(R.id.btnRemoveAll).setOnClickListener {
            // Entferne alle Apps komplett
            if (seenAppsPkgs.isEmpty()) {
                Toast.makeText(this, "Keine Apps vorhanden", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            AlertDialog.Builder(this)
                .setTitle("Alle löschen?")
                .setMessage("Wirklich alle ${seenAppsPkgs.size} App(s) komplett löschen?")
                .setPositiveButton("Ja") { _, _ ->
                    prefs.edit().putStringSet(seenAppsKey, setOf()).apply()

                    seenAppsPkgs.clear()
                    seenApps.clear()
                    adapter.notifyDataSetChanged()
                    Toast.makeText(this, "Alle Apps wurden gelöscht", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Abbrechen", null)
                .show()
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
