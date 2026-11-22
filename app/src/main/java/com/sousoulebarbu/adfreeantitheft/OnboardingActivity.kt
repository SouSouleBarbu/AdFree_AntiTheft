package com.sousoulebarbu.adfreeantitheft

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.sousoulebarbu.adfreeantitheft.databinding.ActivityOnboardingBinding
import kotlinx.coroutines.launch

class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding
    private lateinit var settingsRepository: SettingsRepository

    private var step1Done = false
    private var step2Done = false

    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                markStepDone(1)
            } else {
                Toast.makeText(
                    this,
                    R.string.onboarding_step_notifications_desc,
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        settingsRepository = SettingsRepository(this)

        // L'onboarding est obligatoire : on bloque le bouton retour.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Ne rien faire : impossible de quitter sans valider les étapes.
            }
        })

        binding.cardStep1.setOnClickListener { onStepClicked(1) }
        binding.cardStep2.setOnClickListener { onStepClicked(2) }

        binding.btnContinue.setOnClickListener { finishOnboarding() }

        refreshUi()
    }

    override fun onResume() {
        super.onResume()
        // L'utilisateur revient des paramètres système : on rafraîchit l'état.
        refreshUi()
    }

    private fun onStepClicked(step: Int) {
        when (step) {
            1 -> if (!step1Done) requestNotifications()
            2 -> if (!step2Done) requestBatteryOptimization()
        }
    }

    private fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                markStepDone(1)
            } else {
                // Première demande ou refus antérieur : on relance le dialog système.
                requestNotificationPermissionLauncher.launch(
                    Manifest.permission.POST_NOTIFICATIONS
                )
            }
        } else {
            // Avant Android 13, pas de permission runtime à demander.
            markStepDone(1)
        }
    }

    private fun requestBatteryOptimization() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager

        if (powerManager.isIgnoringBatteryOptimizations(packageName)) {
            markStepDone(2)
            return
        }

        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = "package:$packageName".toUri()
            }
            startActivity(intent)
        } catch (_: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (_: Exception) {
                Toast.makeText(
                    this,
                    R.string.onboarding_step_battery_desc,
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun markStepDone(step: Int) {
        when (step) {
            1 -> step1Done = true
            2 -> step2Done = true
        }
        refreshUi()
    }

    /**
     * Rafraîchit l'UI : cases cochées, cartes dégrisées, bouton Continuer.
     * L'étape 2 est grisée tant que l'étape 1 n'est pas validée.
     */
    private fun refreshUi() {
        step1Done = isNotificationGranted()
        step2Done = isBatteryOptimizationDisabled()

        binding.cbStep1.isChecked = step1Done
        binding.cbStep2.isChecked = step2Done

        val step2Enabled = step1Done
        binding.cardStep2.isEnabled = step2Enabled
        binding.cardStep2.alpha = if (step2Enabled) 1f else 0.4f

        binding.btnContinue.isEnabled = step1Done && step2Done
    }

    private fun isNotificationGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun isBatteryOptimizationDisabled(): Boolean {
        val powerManager = getSystemService(PowerManager::class.java)
        return powerManager.isIgnoringBatteryOptimizations(packageName)
    }

    private fun finishOnboarding() {
        lifecycleScope.launch {
            settingsRepository.updateOnboardingCompleted(true)
        }
        finish()
    }
}
