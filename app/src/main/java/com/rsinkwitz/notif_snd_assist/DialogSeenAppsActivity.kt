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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dialog_seen_apps)

        val prefs = getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        val seenAppsPkgs = (prefs.getStringSet(seenAppsKey, setOf()) ?: setOf()).toMutableList().sorted().toMutableList()
        val pm = packageManager
        // Erzeuge eine Liste mit nur App-Namen (Label)
        seenApps = seenAppsPkgs.map { pkg ->
            try {
                val appInfo = pm.getApplicationInfo(pkg, 0)
                pm.getApplicationLabel(appInfo).toString()
            } catch (e: PackageManager.NameNotFoundException) {
                pkg // Fallback: nur Paketname
            }
        }.toMutableList()
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, seenApps)
        val listView = findViewById<ListView>(R.id.listSeenApps)
        listView.adapter = adapter

        listView.setOnItemClickListener { _, _, position, _ ->
            val pkg = seenAppsPkgs[position]
            val appLabel = seenApps[position]
            AlertDialog.Builder(this)
                .setTitle("Eintrag löschen?")
                .setMessage("App entfernen: $appLabel?")
                .setPositiveButton("Löschen") { _, _ ->
                    seenAppsPkgs.removeAt(position)
                    prefs.edit().putStringSet(seenAppsKey, seenAppsPkgs.toSet()).apply()
                    seenApps.removeAt(position)
                    adapter.notifyDataSetChanged()
                    Toast.makeText(this, "$appLabel entfernt", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Abbrechen", null)
                .show()
        }

        findViewById<Button>(R.id.btnClearAll).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Alle löschen?")
                .setMessage("Wirklich alle Einträge entfernen?")
                .setPositiveButton("Ja") { _, _ ->
                    seenAppsPkgs.clear()
                    prefs.edit().putStringSet(seenAppsKey, setOf()).apply()
                    seenApps.clear()
                    adapter.notifyDataSetChanged()
                    Toast.makeText(this, "Alle Einträge entfernt", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Abbrechen", null)
                .show()
        }
    }
}
