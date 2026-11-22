package com.sousoulebarbu.adfreeantitheft

import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat

class BiometricHelper(
    private val activity: AppCompatActivity,
    private val onSuccess: () -> Unit
) {

    private companion object {
        private const val TAG = "AntiTheftDebug"
    }

    private val executor = ContextCompat.getMainExecutor(activity)
    private val biometricPrompt: BiometricPrompt
    private val promptInfo: BiometricPrompt.PromptInfo

    init {
        biometricPrompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    Log.d(TAG, "Authentification biométrique réussie.")
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    Log.d(TAG, "Erreur d'authentification biométrique ($errorCode): $errString")
                    if (errorCode != BiometricPrompt.ERROR_USER_CANCELED && errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                        Toast.makeText(activity.applicationContext, errString, Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    Log.d(TAG, "Échec d'authentification biométrique (empreinte non reconnue).")
                    Toast.makeText(
                        activity.applicationContext,
                        activity.getString(R.string.biometric_failed),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )

        promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(activity.getString(R.string.biometric_title))
            .setSubtitle(activity.getString(R.string.biometric_subtitle))
            // BiometricPrompt interdit DEVICE_CREDENTIAL combiné à un negative button :
            // l'annulation reste possible via le système (icône/geste retour).
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            .build()
    }

    fun authenticate() {
        val biometricManager = BiometricManager.from(activity)
        val allowed = BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.BIOMETRIC_WEAK or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        val canAuthenticate = biometricManager.canAuthenticate(allowed)

        if (canAuthenticate == BiometricManager.BIOMETRIC_SUCCESS) {
            biometricPrompt.authenticate(promptInfo)
        } else {
            Log.d(TAG, "Biométrie indisponible sur cet appareil. Contournement automatique.")
            Toast.makeText(activity, activity.getString(R.string.biometric_not_available), Toast.LENGTH_LONG).show()
            onSuccess()
        }
    }
}