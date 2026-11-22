package com.sousoulebarbu.adfreeantitheft

import android.content.Intent
import android.media.AudioManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.sousoulebarbu.adfreeantitheft.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "AntiTheftDebug"
        private const val KEY_UI_STATE = "KEY_UI_STATE"
        private const val CALIBRATION_MS = 3000L
        private const val CALIBRATION_SEC = 3
        const val EXTRA_SKIP_DND_CHECK = "EXTRA_SKIP_DND_CHECK"
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var settingsRepository: SettingsRepository
    private val stateHandler = Handler(Looper.getMainLooper())
    private var currentState: UIState = UIState.INACTIVE
    private var countDownTimer: CountDownTimer? = null

    private lateinit var biometricHelper: BiometricHelper

    /** Vrai tant que le test du flux STREAM_ALARM n'a pas été effectué après l'onboarding. */
    private var dndAlarmCheckPending = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setupLockscreenFlags()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        settingsRepository = SettingsRepository(this)

        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        biometricHelper = BiometricHelper(this) {
            onBiometricSuccess()
        }

        if (savedInstanceState != null) {
            val savedStateName =
                savedInstanceState.getString(KEY_UI_STATE, UIState.INACTIVE.name)

            currentState = try {
                UIState.valueOf(savedStateName)
            } catch (_: IllegalArgumentException) {
                UIState.INACTIVE
            }
        }

        lifecycleScope.launch {
            val settings = settingsRepository.getSettingsSnapshot()
            if (!settings.onboardingCompleted) {
                startActivity(Intent(this@MainActivity, OnboardingActivity::class.java))
            }
        }

        checkAlarmIntent(intent)

        binding.btnMainAction.setOnClickListener {
            handleMainActionClick()
        }

        updateUI(currentState)
    }

    override fun onResume() {
        super.onResume()

        // Test DND : exécuté une seule fois par lancement, une fois l'onboarding terminé.
        if (dndAlarmCheckPending) {
            lifecycleScope.launch {
                val settings = settingsRepository.getSettingsSnapshot()
                if (settings.onboardingCompleted) {
                    dndAlarmCheckPending = false
                    if (!settings.ignoreAlarmBlockedAlways) {
                        checkDndAlarmStream()
                    }
                }
            }
        }
    }

    /**
     * Force le volume du flux ALARM au maximum, puis vérifie si le changement a bien été appliqué.
     * Si le volume reste à 0 ou si le flux est muet (ex. : mode DND bloquant), l'accueil est
     * remplacé par l'écran "Alarme bloquée".
     */
    private fun checkDndAlarmStream() {
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        try {
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVolume, 0)
        } catch (e: SecurityException) {
            // Certains constructeurs lèvent une SecurityException si DND bloque le changement de volume
            Log.w("test dnd", "SecurityException levée par le mode DND", e)
        }

        val finalVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
        val finalMute = audioManager.isStreamMute(AudioManager.STREAM_ALARM)

        val alarmBlocked = finalVolume == 0 || finalMute
        if (alarmBlocked) {
            startActivity(Intent(this, AlarmBlockedActivity::class.java))
            finish()
        }
    }

    private fun handleMainActionClick() {
        when (currentState) {
            UIState.INACTIVE -> {
                Log.d(TAG, "Clic sur Activer -> Demande biométrique")
                biometricHelper.authenticate()
            }

            UIState.DELAY, UIState.CALIBRATING, UIState.ACTIVE, UIState.ALARM -> {
                Log.d(TAG, "Clic sur Désactiver en état $currentState -> Demande biométrique")
                biometricHelper.authenticate()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        checkAlarmIntent(intent)
    }

    private fun checkAlarmIntent(intent: Intent?) {
        if (intent?.getBooleanExtra("ALARM_TRIGGERED", false) == true) {
            transitionToState(UIState.ALARM)
        }
        if (intent?.getBooleanExtra(EXTRA_SKIP_DND_CHECK, false) == true) {
            dndAlarmCheckPending = false
        }
    }

    private fun setupLockscreenFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
    }

    private fun onBiometricSuccess() {
        if (currentState == UIState.INACTIVE) {
            transitionToState(UIState.DELAY)
        } else {
            stopMonitoringAndReset()
        }
    }

    private fun stopMonitoringAndReset() {
        cancelScheduledTransitions()

        val serviceIntent = Intent(this, AntiTheftService::class.java).apply {
            action = AntiTheftService.ACTION_STOP_SERVICE
        }

        startService(serviceIntent)

        currentState = UIState.INACTIVE
        updateUI(currentState)

        Log.d(TAG, "Surveillance annulée. Retour à l'état INACTIVE.")
    }

    private fun transitionToState(newState: UIState) {
        val previousState = currentState
        cancelScheduledTransitions()

        currentState = newState
        updateUI(currentState)

        Log.d(TAG, "Changement d'état : $previousState -> $newState")

        when (newState) {
            UIState.DELAY -> {
                lifecycleScope.launch {
                    val settings = settingsRepository.getSettingsSnapshot()
                    val delaySec = settings.placementDelaySec
                    val delayMs = delaySec * 1000L
                    Log.d(TAG, "Délai de placement configuré : $delaySec s ($delayMs ms)")

                    startCountdown(delayMs, delaySec) {
                        transitionToState(UIState.CALIBRATING)
                    }
                }
            }

            UIState.CALIBRATING -> {
                val serviceIntent = Intent(this, AntiTheftService::class.java).apply {
                    action = AntiTheftService.ACTION_START_MONITORING
                }

                ContextCompat.startForegroundService(this, serviceIntent)

                startCountdown(CALIBRATION_MS, CALIBRATION_SEC) {
                    transitionToState(UIState.ACTIVE)
                }
            }

            UIState.ACTIVE, UIState.ALARM, UIState.INACTIVE -> Unit
        }
    }

    private fun startCountdown(totalMs: Long, totalSec: Int, onFinish: () -> Unit) {
        binding.tvCountdown.visibility = View.VISIBLE
        binding.tvCountdown.text = totalSec.toString()

        countDownTimer?.cancel()
        countDownTimer = object : CountDownTimer(totalMs, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                val secondsLeft = ((millisUntilFinished + 999L) / 1000L).toInt()
                binding.tvCountdown.text = secondsLeft.toString()
            }

            override fun onFinish() {
                onFinish()
            }
        }.start()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(KEY_UI_STATE, currentState.name)
        super.onSaveInstanceState(outState)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (currentState != UIState.INACTIVE &&
            (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN ||
                    keyCode == KeyEvent.KEYCODE_VOLUME_MUTE)
        ) {
            Log.d(TAG, "Tentative de baisse du volume bloquée !")
            return true
        }

        return super.onKeyDown(keyCode, event)
    }

    private fun cancelScheduledTransitions() {
        stateHandler.removeCallbacksAndMessages(null)
        countDownTimer?.cancel()
        countDownTimer = null
    }

    private fun setCircleStyles(
        haloColor: Int,
        ringColor: Int,
        discFillColor: Int,
        discStrokeColor: Int = Color.TRANSPARENT
    ) {
        binding.viewHaloOuter.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(haloColor)
        }

        binding.viewRing.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.TRANSPARENT)
            setStroke(dpToPx(3), ringColor)
        }

        binding.viewCenterDisc.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(discFillColor)
            if (discStrokeColor != Color.TRANSPARENT) {
                setStroke(dpToPx(2), discStrokeColor)
            }
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    private fun updateUI(state: UIState) {
        // Synchronise la phase globale (détermine si l'app peut être tuée en arrière-plan).
        AppGuard.phase = AppGuard.Phase.valueOf(state.name)

        val colorGreen = ContextCompat.getColor(this, R.color.state_green)
        val colorRed = ContextCompat.getColor(this, R.color.state_red)
        val colorOrange = ContextCompat.getColor(this, R.color.state_orange)
        val colorSurface = ContextCompat.getColor(this, R.color.surface_dark)

        val textOnGreen = ContextCompat.getColor(this, R.color.text_on_green)
        val textOnRed = ContextCompat.getColor(this, R.color.text_on_red)
        val textPrimary = ContextCompat.getColor(this, R.color.text_primary)
        val textSecondary = ContextCompat.getColor(this, R.color.text_secondary)
        val textAlarm = ContextCompat.getColor(this, R.color.status_alarm_text)

        when (state) {
            UIState.INACTIVE -> {
                binding.btnSettings.visibility = View.VISIBLE

                setCircleStyles(
                    haloColor = ColorUtils.setAlphaComponent(colorGreen, (0.10 * 255).toInt()),
                    ringColor = ColorUtils.setAlphaComponent(colorGreen, (0.25 * 255).toInt()),
                    discFillColor = colorGreen
                )

                binding.ivButtonIcon.setImageResource(R.drawable.ic_shield_off)
                binding.ivButtonIcon.imageTintList = null

                binding.tvButtonLabel.text = getString(R.string.btn_activate)
                binding.tvButtonLabel.setTextColor(textOnGreen)
                binding.tvButtonLabel.visibility = View.VISIBLE

                binding.tvStatus.text = getString(R.string.status_inactive)
                binding.tvStatus.setTextColor(textSecondary)

                binding.tvCountdown.visibility = View.GONE
            }

            UIState.DELAY -> {
                binding.btnSettings.visibility = View.GONE

                setCircleStyles(
                    haloColor = ColorUtils.setAlphaComponent(colorOrange, (0.08 * 255).toInt()),
                    ringColor = ColorUtils.setAlphaComponent(colorOrange, (0.35 * 255).toInt()),
                    discFillColor = colorSurface,
                    discStrokeColor = ColorUtils.setAlphaComponent(colorOrange, (0.45 * 255).toInt())
                )

                binding.ivButtonIcon.setImageResource(R.drawable.ic_phone_arrow)
                binding.ivButtonIcon.imageTintList = ColorStateList.valueOf(colorOrange)

                binding.tvButtonLabel.visibility = View.GONE

                binding.tvStatus.text = getString(R.string.status_delay)
                binding.tvStatus.setTextColor(textPrimary)

                binding.tvCountdown.setTextColor(colorOrange)
                binding.tvCountdown.visibility = View.VISIBLE
            }

            UIState.CALIBRATING -> {
                binding.btnSettings.visibility = View.GONE

                setCircleStyles(
                    haloColor = ColorUtils.setAlphaComponent(colorOrange, (0.08 * 255).toInt()),
                    ringColor = ColorUtils.setAlphaComponent(colorOrange, (0.35 * 255).toInt()),
                    discFillColor = colorSurface,
                    discStrokeColor = ColorUtils.setAlphaComponent(colorOrange, (0.45 * 255).toInt())
                )

                binding.ivButtonIcon.setImageResource(R.drawable.ic_target)
                binding.ivButtonIcon.imageTintList = ColorStateList.valueOf(colorOrange)

                binding.tvButtonLabel.visibility = View.GONE

                binding.tvStatus.text = getString(R.string.status_calibrating)
                binding.tvStatus.setTextColor(textPrimary)

                binding.tvCountdown.setTextColor(colorOrange)
                binding.tvCountdown.visibility = View.VISIBLE
            }

            UIState.ACTIVE -> {
                binding.btnSettings.visibility = View.GONE

                setCircleStyles(
                    haloColor = ColorUtils.setAlphaComponent(colorRed, (0.10 * 255).toInt()),
                    ringColor = ColorUtils.setAlphaComponent(colorRed, (0.25 * 255).toInt()),
                    discFillColor = colorRed
                )

                binding.ivButtonIcon.setImageResource(R.drawable.ic_shield)
                binding.ivButtonIcon.imageTintList = ColorStateList.valueOf(textOnRed)

                binding.tvButtonLabel.text = getString(R.string.btn_deactivate)
                binding.tvButtonLabel.setTextColor(textOnRed)
                binding.tvButtonLabel.visibility = View.VISIBLE

                binding.tvStatus.text = getString(R.string.status_active)
                binding.tvStatus.setTextColor(textPrimary)

                binding.tvCountdown.visibility = View.GONE
            }

            UIState.ALARM -> {
                binding.btnSettings.visibility = View.GONE

                setCircleStyles(
                    haloColor = ColorUtils.setAlphaComponent(colorRed, (0.20 * 255).toInt()),
                    ringColor = ColorUtils.setAlphaComponent(colorRed, (0.90 * 255).toInt()),
                    discFillColor = colorRed
                )

                binding.ivButtonIcon.setImageResource(R.drawable.ic_alert)
                binding.ivButtonIcon.imageTintList = null

                binding.tvButtonLabel.text = getString(R.string.btn_deactivate)
                binding.tvButtonLabel.setTextColor(textOnRed)
                binding.tvButtonLabel.visibility = View.VISIBLE

                binding.tvStatus.text = getString(R.string.status_alarm)
                binding.tvStatus.setTextColor(textAlarm)

                binding.tvCountdown.visibility = View.GONE
            }
        }
    }

    override fun onDestroy() {
        cancelScheduledTransitions()
        super.onDestroy()
    }
}
