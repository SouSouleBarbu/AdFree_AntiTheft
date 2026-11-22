package com.sousoulebarbu.adfreeantitheft

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import java.util.Collections
import java.util.WeakHashMap

/**
 * Gère le son + la vibration + le volume de l'alarme.
 * Factorisé pour être partagé entre la vraie alarme (AntiTheftService),
 * le test des paramètres (SettingsActivity) et l'écran "Alarme bloquée".
 */
class AlarmController(private val context: Context, private val tag: String = "AlarmController") {

    companion object {
        /** Instances dont l'alarme sonne actuellement, pour tout arrêter d'un coup. */
        private val liveInstances =
            Collections.newSetFromMap<AlarmController>(WeakHashMap())

        /** Arrête toutes les alarmes en cours (libération du MediaPlayer et du vibreur). */
        fun stopAll() {
            liveInstances.toList().forEach { it.stop() }
        }
    }

    private var mediaPlayer: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())

    var isRinging = false
        private set

    private val volumeEnforcerRunnable = object : Runnable {
        override fun run() {
            if (isRinging) {
                forceMaxVolume()
                handler.postDelayed(this, 200)
            }
        }
    }

    /** Déclenche l'alarme (son en boucle + vibration + volume alarme au max). */
    fun start() {
        if (isRinging) return
        isRinging = true
        liveInstances.add(this)

        forceMaxVolume()

        try {
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )

                val afd = context.resources.openRawResourceFd(R.raw.my_alarm_sound)
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()

                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.e(tag, "Erreur lecture audio alarme", e)
        }

        startVibrator()
        handler.post(volumeEnforcerRunnable)
    }

    /** Arrête l'alarme et libère toutes les ressources associées. */
    fun stop() {
        if (!isRinging) return
        isRinging = false
        liveInstances.remove(this)

        handler.removeCallbacks(volumeEnforcerRunnable)
        stopAudio()
        stopVibration()
    }

    private fun forceMaxVolume() {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        try {
            val maxAlarmVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            if (audioManager.getStreamVolume(AudioManager.STREAM_ALARM) != maxAlarmVol) {
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxAlarmVol, 0)
            }
        } catch (e: Exception) {
            Log.w(tag, "Impossible de forcer les volumes.", e)
        }
    }

    private fun startVibrator() {
        val pattern = longArrayOf(0, 500, 200, 500)

        val vibrator = defaultVibrator()
        if (vibrator == null || !vibrator.hasVibrator()) {
            Log.w(tag, "Aucun vibreur disponible sur cet appareil.")
            return
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // USAGE_ALARM : la vibration n'est pas bloquée par Ne pas déranger
                vibrator.vibrate(
                    VibrationEffect.createWaveform(pattern, 0),
                    VibrationAttributes.Builder()
                        .setUsage(VibrationAttributes.USAGE_ALARM)
                        .build()
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(
                    VibrationEffect.createWaveform(pattern, 0),
                    alarmAudioAttributes()
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(pattern, 0)
            }
        } catch (e: Exception) {
            Log.w(tag, "Erreur lors du déclenchement de la vibration", e)
        }
    }

    private fun defaultVibrator(): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

    private fun alarmAudioAttributes(): AudioAttributes =
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

    private fun stopAudio() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            }
        } catch (e: Exception) {
            Log.w(tag, "Erreur arrêt MediaPlayer", e)
        } finally {
            mediaPlayer = null
        }
    }

    private fun stopVibration() {
        try {
            defaultVibrator()?.cancel()
        } catch (e: Exception) {
            Log.w(tag, "Erreur arrêt vibration", e)
        }
    }
}
