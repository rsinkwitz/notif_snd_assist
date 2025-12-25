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
        setTitle("Offene Benachrichtigungs-Apps")

        val prefs = getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        pendingPkgs = (prefs.getStringSet(pendingKey, setOf()) ?: setOf()).toMutableList().sorted().toMutableList()
        val pm = packageManager
        pendingLabels = pendingPkgs.map { pkg ->
            try {
                val appInfo = pm.getApplicationInfo(pkg, 0)
                pm.getApplicationLabel(appInfo).toString()
            } catch (e: PackageManager.NameNotFoundException) {
                pkg
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
                .setTitle("Alle entfernen?")
                .setMessage("Wirklich alle offenen Apps als erledigt markieren?")
                .setPositiveButton("Ja") { _, _ ->
                    val seen = prefs.getStringSet(seenKey, setOf())?.toMutableSet() ?: mutableSetOf()
                    seen.addAll(pendingPkgs)
                    prefs.edit().putStringSet(seenKey, seen).putStringSet(pendingKey, setOf()).apply()
                    pendingPkgs.clear()
                    pendingLabels.clear()
                    adapter.notifyDataSetChanged()
                    Toast.makeText(this, "Alle als erledigt markiert", Toast.LENGTH_SHORT).show()
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
        Toast.makeText(this, "Wähle dort 'Benachrichtigungen' und dann die passende Kategorie, um den Sound zu ändern.", Toast.LENGTH_LONG).show()
    }
}
