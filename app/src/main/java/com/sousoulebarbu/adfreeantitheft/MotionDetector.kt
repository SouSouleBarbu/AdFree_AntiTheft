package com.sousoulebarbu.adfreeantitheft

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import kotlin.math.abs

class MotionDetector(
    context: Context,
    private val onAlarmTriggered: () -> Unit
) : SensorEventListener {

    private companion object {
        private const val TAG = "AntiTheftDebug"
    }

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val samplesX = mutableListOf<Float>()
    private val samplesY = mutableListOf<Float>()
    private val samplesZ = mutableListOf<Float>()

    private var baselineX = 0f
    private var baselineY = 0f
    private var baselineZ = 0f

    private var isCalibrating = false
    private var isMonitoring = false

    private var lastSampleTime = 0L
    private var consecutiveExceededCount = 0

    var sensitivityLimit: Float = AppSettings.DEFAULT_SENSITIVITY_LIMIT
    var requiredExceededCount: Int = AppSettings.DEFAULT_CONSECUTIVE_EXCEEDED_COUNT
    var checkIntervalMs: Long = AppSettings.DEFAULT_CHECK_INTERVAL_MS

    fun updateConfig(
        sensitivityLimit: Float,
        requiredCount: Int,
        checkIntervalMs: Long
    ) {
        this.sensitivityLimit = sensitivityLimit
        this.requiredExceededCount = requiredCount
        this.checkIntervalMs = checkIntervalMs
        Log.d(
            TAG,
            "MotionDetector Config mis à jour : sensitivity=%.2f, requiredCount=$requiredCount, interval=${checkIntervalMs}ms"
                .format(sensitivityLimit)
        )
    }

    fun startCalibration() {
        stop()
        samplesX.clear()
        samplesY.clear()
        samplesZ.clear()

        accelerometer?.let { sensor ->
            isCalibrating = true
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
            Log.d(TAG, "Calibration démarrée...")
        } ?: Log.w(TAG, "Accéléromètre indisponible.")
    }

    fun stopCalibrationAndCompute() {
        isCalibrating = false
        if (samplesX.isNotEmpty()) {
            baselineX = samplesX.average().toFloat()
            baselineY = samplesY.average().toFloat()
            baselineZ = samplesZ.average().toFloat()
            Log.d(TAG, "Calibration OK -> Baseline: X=%.2f, Y=%.2f, Z=%.2f".format(baselineX, baselineY, baselineZ))
        }
    }

    fun startMonitoring() {
        consecutiveExceededCount = 0
        isMonitoring = true
        accelerometer?.let { sensor ->
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
            Log.d(TAG, "Surveillance des mouvements activée.")
        }
    }

    fun stop() {
        isCalibrating = false
        isMonitoring = false
        consecutiveExceededCount = 0
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        val now = System.currentTimeMillis()

        if (isCalibrating) {
            samplesX.add(event.values[0])
            samplesY.add(event.values[1])
            samplesZ.add(event.values[2])
            return
        }

        if (isMonitoring) {
            if (now - lastSampleTime >= checkIntervalMs) {
                lastSampleTime = now

                val diffX = abs(event.values[0] - baselineX)
                val diffY = abs(event.values[1] - baselineY)
                val diffZ = abs(event.values[2] - baselineZ)

                if (diffX > sensitivityLimit || diffY > sensitivityLimit || diffZ > sensitivityLimit) {
                    consecutiveExceededCount++
                    Log.d(
                        TAG,
                        "Dépassement détecté ($consecutiveExceededCount/$requiredExceededCount) -> X:%.2f, Y:%.2f, Z:%.2f"
                            .format(diffX, diffY, diffZ)
                    )

                    if (consecutiveExceededCount >= requiredExceededCount) {
                        Log.e(TAG, "🚨 ALARME ! $requiredExceededCount dépassements consécutifs de ${checkIntervalMs}ms !")
                        isMonitoring = false
                        onAlarmTriggered()
                    }
                } else {
                    if (consecutiveExceededCount > 0) {
                        Log.d(TAG, "Mouvement interrompu, compteur réinitialisé.")
                        consecutiveExceededCount = 0
                    }
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
