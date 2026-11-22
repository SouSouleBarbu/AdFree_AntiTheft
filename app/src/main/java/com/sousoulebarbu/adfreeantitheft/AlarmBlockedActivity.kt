package com.sousoulebarbu.adfreeantitheft

import android.app.Activity
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.sousoulebarbu.adfreeantitheft.databinding.ActivityAlarmBlockedBinding
import kotlinx.coroutines.launch

class AlarmBlockedActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAlarmBlockedBinding

    private lateinit var alarmController: AlarmController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        alarmController = AlarmController(applicationContext)

        binding = ActivityAlarmBlockedBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        binding.btnTestAlarm.setOnClickListener {
            if (alarmController.isRinging) {
                alarmController.stop()
                binding.btnTestAlarm.setText(R.string.alarm_blocked_test_alarm)
            } else {
                alarmController.start()
                binding.btnTestAlarm.setText(R.string.alarm_blocked_stop_alarm)
            }
        }

        binding.btnOpenSettings.setOnClickListener {
            openAlarmSoundSettings(this)
        }

        binding.btnIgnoreOnce.setOnClickListener {
            alarmController.stop()
            val intent = Intent(this, MainActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_SKIP_DND_CHECK, true)
            }
            startActivity(intent)
            finish()
        }

        val settingsRepository = SettingsRepository(this)
        binding.btnIgnoreAlways.setOnClickListener {
            alarmController.stop()
            lifecycleScope.launch {
                settingsRepository.updateIgnoreAlarmBlockedAlways(true)
                val intent = Intent(this@AlarmBlockedActivity, MainActivity::class.java).apply {
                    putExtra(MainActivity.EXTRA_SKIP_DND_CHECK, true)
                }
                startActivity(intent)
                finish()
            }
        }
    }

    private fun openAlarmSoundSettings(context: Context) {
        val flags = if (context !is Activity) {
            Intent.FLAG_ACTIVITY_NEW_TASK
        } else {
            0
        }

        val intents = mutableListOf<Intent>()

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val filter = notificationManager.currentInterruptionFilter

        val isDndPotentiallyBlockingAlarm =
            filter == NotificationManager.INTERRUPTION_FILTER_PRIORITY ||
                    filter == NotificationManager.INTERRUPTION_FILTER_NONE

        // 1. Paramètres DND si potentiellement bloquant
        if (isDndPotentiallyBlockingAlarm &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
        ) {
            intents.add(Intent(Settings.ACTION_ZEN_MODE_PRIORITY_SETTINGS))
        }

        // 2. Paramètres sonores
        intents.add(Intent(Settings.ACTION_SOUND_SETTINGS))

        // 3. Paramètres généraux
        intents.add(Intent(Settings.ACTION_SETTINGS))

        for (intent in intents) {
            try {
                if (flags != 0) {
                    intent.addFlags(flags)
                }
                context.startActivity(intent)
                return
            } catch (e: ActivityNotFoundException) {
                // Essayer l'écran suivant
            } catch (e: SecurityException) {
                // Essayer l'écran suivant
            }
        }
    }

    override fun onDestroy() {
        alarmController.stop()
        super.onDestroy()
    }
}
