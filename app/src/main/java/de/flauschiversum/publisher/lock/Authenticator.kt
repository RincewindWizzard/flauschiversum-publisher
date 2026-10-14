package de.flauschiversum.publisher.lock

import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

enum class LockAvailability { AVAILABLE, NOT_SET_UP, UNSUPPORTED }
enum class AuthResult { SUCCESS, FAILED, CANCELLED }

/** Geräte-Authentifizierung (Fingerabdruck, Gesicht, PIN, Muster). Es gibt keine App-eigene Passwortumgehung. */
interface Authenticator {
    fun availability(context: Context): LockAvailability
    suspend fun authenticate(context: Context, title: String): AuthResult
}

class BiometricAuthenticator : Authenticator {
    private val allowed = BIOMETRIC_WEAK or DEVICE_CREDENTIAL

    override fun availability(context: Context): LockAvailability = when (BiometricManager.from(context).canAuthenticate(allowed)) {
        BiometricManager.BIOMETRIC_SUCCESS -> LockAvailability.AVAILABLE
        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> LockAvailability.NOT_SET_UP
        else -> LockAvailability.UNSUPPORTED
    }

    private fun activityOf(context: Context): FragmentActivity? {
        var c: Context? = context
        while (c is ContextWrapper) { if (c is FragmentActivity) return c; c = c.baseContext }
        return null
    }

    override suspend fun authenticate(context: Context, title: String): AuthResult {
        val activity = activityOf(context) ?: return AuthResult.FAILED
        return suspendCancellableCoroutine { cont ->
            val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { if (cont.isActive) cont.resume(AuthResult.SUCCESS) }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    val cancelled = errorCode == BiometricPrompt.ERROR_USER_CANCELED || errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON || errorCode == BiometricPrompt.ERROR_CANCELED
                    if (cont.isActive) cont.resume(if (cancelled) AuthResult.CANCELLED else AuthResult.FAILED)
                }
            })
            val info = BiometricPrompt.PromptInfo.Builder().setTitle(title).setAllowedAuthenticators(allowed).build()
            prompt.authenticate(info)
            cont.invokeOnCancellation { runCatching { prompt.cancelAuthentication() } }
        }
    }
}
