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
        setTitle("Configure App")

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
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, pendingLabels)
        val listView = findViewById<ListView>(R.id.listSeenApps)
        listView.adapter = adapter

        listView.setOnItemClickListener { _, _, position, _ ->
            val pkg = pendingPkgs[position]
            val appLabel = pendingLabels[position].substringBefore('\n')
            AlertDialog.Builder(this)
                .setTitle("Einstellungen öffnen?")
                .setMessage("Sound-Einstellungen für $appLabel öffnen?")
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
                    Toast.makeText(this, "$appLabel als erledigt markiert", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Abbrechen", null)
                .show()
        }

        findViewById<Button>(R.id.btnClearAll).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Clear list?")
                .setMessage("Really clear list?")
                .setPositiveButton("Yes") { _, _ ->
                    val seen = prefs.getStringSet(seenKey, setOf())?.toMutableSet() ?: mutableSetOf()
                    seen.addAll(pendingPkgs)
                    prefs.edit().putStringSet(seenKey, seen).putStringSet(pendingKey, setOf()).apply()
                    pendingPkgs.clear()
                    pendingLabels.clear()
                    adapter.notifyDataSetChanged()
                    Toast.makeText(this, "Pending app list cleared", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun openAppSettings(pkg: String) {
        val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = android.net.Uri.parse("package:$pkg")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
        Toast.makeText(this, "To change sound, select 'Notifications', then 'categories'", Toast.LENGTH_LONG).show()
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
