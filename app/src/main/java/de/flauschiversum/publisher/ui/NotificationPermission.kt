package de.flauschiversum.publisher.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.core.app.ActivityCompat

/**
 * Bittet um die Berechtigung für Benachrichtigungen. Der Activity-Result-Launcher scheidet aus: Er vergibt
 * Anfragecodes über 16 Bit, die eine `FragmentActivity` (nötig für BiometricPrompt) ablehnt. Das Ergebnis wird nicht
 * ausgewertet, denn die App arbeitet auch ohne die Berechtigung.
 */
object NotificationPermission {
    const val REQUEST_CODE = 1001

    fun request(context: Context) {
        val activity = generateSequence(context) { (it as? ContextWrapper)?.baseContext }.filterIsInstance<Activity>().firstOrNull() ?: return
        ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_CODE)
    }
}
