package com.sousoulebarbu.adfreeantitheft

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.slider.Slider
import com.sousoulebarbu.adfreeantitheft.databinding.ActivitySettingsBinding
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private companion object {
        private const val TAG = "SettingsActivity"
    }

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var settingsRepository: SettingsRepository

    private var currentPlacementDelaySec = AppSettings.DEFAULT_PLACEMENT_DELAY_SEC
    private var isUpdatingUiFromFlow = false

    private val alarmController = AlarmController(this, TAG)
    private var isTestAlarmRinging = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        settingsRepository = SettingsRepository(this)

        setupListeners()
        observeSettings()
        setupBackPressedHandler()
    }

    private fun setupBackPressedHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.layoutTestAlarmOverlay.visibility == View.VISIBLE) {
                    stopTestAlarm()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnDelayMinus.setOnClickListener {
            if (currentPlacementDelaySec > 1) {
                val newValue = currentPlacementDelaySec - 1
                lifecycleScope.launch {
                    settingsRepository.updatePlacementDelaySec(newValue)
                }
            }
        }

        binding.btnDelayPlus.setOnClickListener {
            if (currentPlacementDelaySec < 30) {
                val newValue = currentPlacementDelaySec + 1
                lifecycleScope.launch {
                    settingsRepository.updatePlacementDelaySec(newValue)
                }
            }
        }

        binding.sliderSensitivity.addOnChangeListener { _, value, _ ->
            binding.tvSensitivityValue.text =
                getString(R.string.settings_sensitivity_value, value)
        }

        binding.sliderSensitivity.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {}
            override fun onStopTrackingTouch(slider: Slider) {
                if (!isUpdatingUiFromFlow) {
                    lifecycleScope.launch {
                        settingsRepository.updateSensitivityLimit(slider.value)
                    }
                }
            }
        })

        binding.sliderConsecutive.addOnChangeListener { _, value, _ ->
            binding.tvConsecutiveValue.text =
                getString(R.string.settings_consecutive_value, value.toInt())
        }

        binding.sliderConsecutive.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {}
            override fun onStopTrackingTouch(slider: Slider) {
                if (!isUpdatingUiFromFlow) {
                    lifecycleScope.launch {
                        settingsRepository.updateConsecutiveExceededCount(slider.value.toInt())
                    }
                }
            }
        })

        binding.sliderInterval.addOnChangeListener { _, value, _ ->
            binding.tvIntervalValue.text =
                getString(R.string.settings_interval_value, value.toInt())
        }

        binding.sliderInterval.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {}
            override fun onStopTrackingTouch(slider: Slider) {
                if (!isUpdatingUiFromFlow) {
                    lifecycleScope.launch {
                        settingsRepository.updateCheckIntervalMs(slider.value.toLong())
                    }
                }
            }
        })

        binding.switchCableAlarm.setOnCheckedChangeListener { _, isChecked ->
            if (!isUpdatingUiFromFlow) {
                lifecycleScope.launch {
                    settingsRepository.updateCableUnplugAlarmEnabled(isChecked)
                }
            }
        }

        binding.btnTestAlarm.setOnClickListener {
            startTestAlarm()
        }

        binding.btnStopTestAlarm.setOnClickListener {
            stopTestAlarm()
        }

        binding.btnResetSettings.setOnClickListener {
            lifecycleScope.launch {
                settingsRepository.resetToDefaults()
                Toast.makeText(
                    this@SettingsActivity,
                    R.string.settings_reset_confirm,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun observeSettings() {
        lifecycleScope.launch {
            settingsRepository.settingsFlow.collect { settings ->
                isUpdatingUiFromFlow = true

                currentPlacementDelaySec = settings.placementDelaySec
                binding.tvDelayValue.text =
                    getString(R.string.settings_placement_delay_value, settings.placementDelaySec)

                binding.sliderSensitivity.value = settings.sensitivityLimit
                binding.tvSensitivityValue.text =
                    getString(R.string.settings_sensitivity_value, settings.sensitivityLimit)

                binding.sliderConsecutive.value = settings.consecutiveExceededCount.toFloat()
                binding.tvConsecutiveValue.text =
                    getString(R.string.settings_consecutive_value, settings.consecutiveExceededCount)

                binding.sliderInterval.value = settings.checkIntervalMs.toFloat()
                binding.tvIntervalValue.text =
                    getString(R.string.settings_interval_value, settings.checkIntervalMs.toInt())

                binding.switchCableAlarm.isChecked = settings.cableUnplugAlarmEnabled

                isUpdatingUiFromFlow = false
            }
        }
    }

    private fun startTestAlarm() {
        if (isTestAlarmRinging) return
        isTestAlarmRinging = true

        binding.layoutTestAlarmOverlay.visibility = View.VISIBLE

        alarmController.start()
    }

    private fun stopTestAlarm() {
        if (!isTestAlarmRinging && binding.layoutTestAlarmOverlay.visibility == View.GONE) return

        isTestAlarmRinging = false

        binding.layoutTestAlarmOverlay.visibility = View.GONE

        alarmController.stop()
    }

    override fun onStop() {
        stopTestAlarm()
        super.onStop()
    }

    override fun onDestroy() {
        stopTestAlarm()
        super.onDestroy()
    }
}
