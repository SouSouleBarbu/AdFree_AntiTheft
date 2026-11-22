package com.sousoulebarbu.adfreeantitheft

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper

/**
 * Gère le cycle de vie global de l'application.
 *
 * Lorsque l'application passe en arrière-plan en phase non protégée (![AppGuard.isProtected]),
 * elle se met en pause (les tests d'alarme s'arrêtent). À son retour au premier plan,
 * elle redémarre automatiquement depuis le début (relance de [MainActivity] à zéro avec test
 * automatique du volume d'alarme et fermeture des écrans secondaires).
 */
class App : Application() {

    private var startedActivityCount = 0
    private var needRestartOnForeground = false
    private val mainHandler = Handler(Looper.getMainLooper())

    private val backgroundCheckRunnable = Runnable {
        if (startedActivityCount == 0 && !AppGuard.isProtected()) {
            needRestartOnForeground = true
        }
    }

    override fun onCreate() {
        super.onCreate()

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}

            override fun onActivityStarted(activity: Activity) {
                mainHandler.removeCallbacks(backgroundCheckRunnable)

                val cameFromBackground = (startedActivityCount == 0)
                startedActivityCount++

                if (cameFromBackground && needRestartOnForeground) {
                    needRestartOnForeground = false
                    if (!AppGuard.isProtected()) {
                        restartAppFromScratch(activity)
                    }
                }
            }

            override fun onActivityResumed(activity: Activity) {}

            override fun onActivityPaused(activity: Activity) {}

            override fun onActivityStopped(activity: Activity) {
                startedActivityCount--
                if (startedActivityCount <= 0) {
                    startedActivityCount = 0
                    mainHandler.postDelayed(backgroundCheckRunnable, BACKGROUND_GRACE_MS)
                }
            }

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    private fun restartAppFromScratch(currentActivity: Activity) {
        try {
            AlarmController.stopAll()
        } catch (_: Exception) {
        }

        val intent = Intent(currentActivity, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        currentActivity.startActivity(intent)
    }

    private companion object {
        private const val BACKGROUND_GRACE_MS = 300L
    }
}
