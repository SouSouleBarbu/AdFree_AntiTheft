package com.sousoulebarbu.adfreeantitheft

import android.os.Process
import kotlin.system.exitProcess

/**
 * Phase globale de l'application, partagée entre les écrans et le service.
 *
 * Tant que la phase est protégée (temporisation pour poser le téléphone, calibration,
 * surveillance active, alarme déclenchée), le processus doit rester vivant même quand
 * l'application n'est plus au premier plan. Sinon (état INACTIVE), l'application se tue
 * dès qu'elle quitte le premier plan.
 */
object AppGuard {

    enum class Phase {
        INACTIVE,
        DELAY,
        CALIBRATING,
        ACTIVE,
        ALARM
    }

    @Volatile
    var phase: Phase = Phase.INACTIVE

    /** Vrai si l'app doit rester vivante en arrière-plan. */
    fun isProtected(): Boolean = phase != Phase.INACTIVE

    /**
     * Arrête proprement les alarmes en cours (libération du MediaPlayer et du vibreur)
     * puis tue le processus courant.
     */
    fun killApp() {
        try {
            AlarmController.stopAll()
        } catch (_: Exception) {
        }
        Process.killProcess(Process.myPid())
        exitProcess(0)
    }
}
