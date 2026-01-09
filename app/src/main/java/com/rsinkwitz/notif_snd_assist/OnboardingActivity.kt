package com.rsinkwitz.notif_snd_assist

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class OnboardingActivity : AppCompatActivity() {

    private var currentStep = 1

    private lateinit var tvStepTitle: TextView
    private lateinit var tvStepDescription: TextView
    private lateinit var btnNext: Button
    private lateinit var btnSkip: Button

    // Permission Launcher für POST_NOTIFICATIONS
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // Berechtigung wurde erteilt
            showStep(4)
        } else {
            // Berechtigung wurde verweigert, aber weiter machen
            showStep(4)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)

        tvStepTitle = findViewById(R.id.tvOnboardingTitle)
        tvStepDescription = findViewById(R.id.tvOnboardingDescription)
        btnNext = findViewById(R.id.btnOnboardingNext)
        btnSkip = findViewById(R.id.btnOnboardingSkip)

        btnNext.setOnClickListener {
            when (currentStep) {
                1 -> {
                    currentStep = 2
                    showStep(2)
                }
                2 -> {
                    requestNotificationListenerPermission()
                }
                3 -> {
                    requestPostNotificationsPermission()
                }
                4 -> {
                    // Onboarding abschließen
                    finishOnboarding()
                }
            }
        }

        btnSkip.setOnClickListener {
            finishOnboarding()
        }

        showStep(1)
    }

    private fun showStep(step: Int) {
        currentStep = step
        when (step) {
            1 -> {
                tvStepTitle.text = getString(R.string.onboarding_step1_title)
                tvStepDescription.text = getString(R.string.onboarding_step1_description)
                btnNext.text = getString(R.string.btn_next)
                btnSkip.visibility = View.VISIBLE
            }
            2 -> {
                tvStepTitle.text = getString(R.string.onboarding_step2_title)
                tvStepDescription.text = getString(R.string.onboarding_step2_description)
                btnNext.text = getString(R.string.btn_grant_permission)
                btnSkip.visibility = View.VISIBLE
            }
            3 -> {
                tvStepTitle.text = getString(R.string.onboarding_step3_title)
                tvStepDescription.text = getString(R.string.onboarding_step3_description)
                btnNext.text = getString(R.string.btn_grant_permission)
                btnSkip.visibility = View.VISIBLE
            }
            4 -> {
                tvStepTitle.text = getString(R.string.onboarding_step4_title)
                tvStepDescription.text = getString(R.string.onboarding_step4_description)
                btnNext.text = getString(R.string.btn_finish)
                btnSkip.visibility = View.GONE
            }
        }
    }

    private fun requestNotificationListenerPermission() {
        val cn = ComponentName(this, NotificationMonitorService::class.java)
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        val enabled = flat != null && flat.contains(cn.flattenToString())

        if (!enabled) {
            AlertDialog.Builder(this)
                .setTitle(R.string.onboarding_listener_dialog_title)
                .setMessage(R.string.onboarding_listener_dialog_message)
                .setPositiveButton(R.string.btn_open_settings) { _, _ ->
                    val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                    startActivity(intent)
                }
                .setNegativeButton(R.string.btn_cancel) { _, _ ->
                    showStep(3)
                }
                .show()
        } else {
            // Permission bereits erteilt
            showStep(3)
        }
    }

    private fun requestPostNotificationsPermission() {
        // Nur für Android 13+ (API 33+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasPermission) {
                // Zeige Erklärung und fordere Berechtigung an
                AlertDialog.Builder(this)
                    .setTitle(R.string.onboarding_step3_title)
                    .setMessage(R.string.onboarding_step3_description)
                    .setPositiveButton(R.string.btn_grant_permission) { _, _ ->
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    .setNegativeButton(R.string.btn_cancel) { _, _ ->
                        showStep(4)
                    }
                    .show()
            } else {
                // Berechtigung bereits erteilt
                showStep(4)
            }
        } else {
            // Für ältere Android-Versionen nicht benötigt
            showStep(4)
        }
    }

    override fun onResume() {
        super.onResume()
        // Prüfe nach Rückkehr von Settings, ob Permission erteilt wurde
        if (currentStep == 2) {
            val cn = ComponentName(this, NotificationMonitorService::class.java)
            val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
            val enabled = flat != null && flat.contains(cn.flattenToString())
            if (enabled) {
                showStep(3)
            }
        }
    }

    private fun finishOnboarding() {
        // Markiere Onboarding als abgeschlossen
        val prefs = getSharedPreferences("notif_snd_assist_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("onboarding_completed", true).apply()

        // Starte NotificationMonitorService
        val serviceIntent = Intent(this, NotificationMonitorService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        // Gehe zur MainActivity
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
}

