package com.sousoulebarbu.adfreeantitheft

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

class AntiTheftService : Service() {

    companion object {
        private const val TAG = "AntiTheftDebug"
        private const val CHANNEL_ID = "anti_theft_channel"
        private const val NOTIF_ID = 1001

        const val ACTION_START_MONITORING = "ACTION_START_MONITORING"
        const val ACTION_TRIGGER_ALARM = "ACTION_TRIGGER_ALARM"
        const val ACTION_STOP_SERVICE = "ACTION_STOP_SERVICE"
    }

    private lateinit var motionDetector: MotionDetector
    private lateinit var settingsRepository: SettingsRepository
    private var cableUnplugAlarmEnabled = true

    private val alarmController = AlarmController(this, TAG)

    private var wakeLock: PowerManager.WakeLock? = null

    private val handler = Handler(Looper.getMainLooper())

    private val calibrationRunnable = Runnable {
        motionDetector.stopCalibrationAndCompute()
        motionDetector.startMonitoring()
        if (cableUnplugAlarmEnabled) {
            registerPowerReceiver()
        }
        Log.d(TAG, "Service : Passé en surveillance active. (Écoute câble: $cableUnplugAlarmEnabled)")
    }
    private fun registerPowerReceiver() {
        if (isPowerReceiverRegistered) return

        val filter = IntentFilter(Intent.ACTION_POWER_DISCONNECTED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(powerConnectionReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(powerConnectionReceiver, filter)
        }
        isPowerReceiverRegistered = true
        Log.d(TAG, "Écoute du débranchement câble activée.")
    }
    private var isPowerReceiverRegistered = false

    private val powerConnectionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_POWER_DISCONNECTED) {
                Log.w(TAG, "Câble débranché pendant la surveillance ! Déclenchement alarme.")
                triggerAlarm()
            }
        }
    }
    override fun onCreate() {
        super.onCreate()

        createNotificationChannel()

        settingsRepository = SettingsRepository(this)
        motionDetector = MotionDetector(this) {
            triggerAlarm()
        }

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "AntiTheft::ServiceWakeLock"
        )
        wakeLock?.acquire()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START_MONITORING

        if (action == ACTION_STOP_SERVICE) {
            cleanUpResources()
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(
            NOTIF_ID,
            createNotification(getString(R.string.notif_surveillance_active))
        )

        when (action) {
            ACTION_START_MONITORING -> {
                val settings = runBlocking(Dispatchers.IO) {
                    settingsRepository.getSettingsSnapshot()
                }
                cableUnplugAlarmEnabled = settings.cableUnplugAlarmEnabled
                motionDetector.updateConfig(
                    sensitivityLimit = settings.sensitivityLimit,
                    requiredCount = settings.consecutiveExceededCount,
                    checkIntervalMs = settings.checkIntervalMs
                )

                Log.d(
                    TAG,
                    "Service : Démarrage calibration avec sensibilité=%.2f, détections=%d, intervalle=%d ms, câble=%b"
                        .format(
                            settings.sensitivityLimit,
                            settings.consecutiveExceededCount,
                            settings.checkIntervalMs,
                            settings.cableUnplugAlarmEnabled
                        )
                )

                handler.removeCallbacks(calibrationRunnable)
                motionDetector.startCalibration()
                handler.postDelayed(calibrationRunnable, 3000)
            }

            ACTION_TRIGGER_ALARM -> triggerAlarm()
        }

        return START_STICKY
    }

    private fun triggerAlarm() {
        if (alarmController.isRinging) return

        AppGuard.phase = AppGuard.Phase.ALARM

        Log.e(TAG, "Service : DÉCLENCHEMENT DE L'ALARME !")

        alarmController.start()

        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("ALARM_TRIGGERED", true)
        }

        startActivity(openIntent)

        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        notificationManager.notify(
            NOTIF_ID,
            createNotification(getString(R.string.notif_alarm_triggered))
        )
    }

    private fun cleanUpResources() {
        AppGuard.phase = AppGuard.Phase.INACTIVE

        if (!::motionDetector.isInitialized) {
            return
        }

        alarmController.stop()

        handler.removeCallbacks(calibrationRunnable)

        unregisterPowerReceiver() // <-- Désenregistrement propre ici

        motionDetector.stop()

        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }

        stopForeground(STOP_FOREGROUND_REMOVE)

        Log.d(TAG, "Service arrêté.")
    }
    private fun unregisterPowerReceiver() {
        if (isPowerReceiverRegistered) {
            try {
                unregisterReceiver(powerConnectionReceiver)
                Log.d(TAG, "Écoute du débranchement câble désactivée.")
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Le récepteur câble n'était pas enregistré.", e)
            } finally {
                isPowerReceiverRegistered = false
            }
        }
    }

    private fun createNotification(content: String): Notification {
        val intent = Intent(this, MainActivity::class.java)

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(content)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            )

            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        cleanUpResources()
        super.onDestroy()
    }
}
